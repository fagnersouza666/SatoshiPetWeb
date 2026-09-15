package br.com.satoshipet.api.art;

import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.art.generation.GenerationRequest;
import br.com.satoshipet.api.art.generation.GenerationResult;
import br.com.satoshipet.api.art.generation.ImageGenerationException;
import br.com.satoshipet.api.art.generation.ImageGenerationPort;
import br.com.satoshipet.api.outbox.OutboxService;
import br.com.satoshipet.api.pet.ArtworkStatus;
import br.com.satoshipet.api.pet.Pet;
import br.com.satoshipet.api.pet.PetPresentation;
import br.com.satoshipet.api.storage.ObjectStoragePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pipeline assíncrono de geração, validação e persistência de sprites (ART-02..04).
 */
@ApplicationScoped
public class ArtworkPipeline {

    private static final Logger LOG = Logger.getLogger(ArtworkPipeline.class);
    static final String PET_ARTWORK_READY = "PET_ARTWORK_READY";

    private final ImageGenerationPort imageGeneration;
    private final ContentGuardrails guardrails;
    private final SpriteValidator validator;
    private final AtlasPackager packager;
    private final ObjectStoragePort storage;
    private final ArtworkContextBuilder contextBuilder;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final GenerationRequest.StubMode stubMode;

    @Inject
    public ArtworkPipeline(
            ImageGenerationPort imageGeneration,
            ContentGuardrails guardrails,
            SpriteValidator validator,
            AtlasPackager packager,
            ObjectStoragePort storage,
            ArtworkContextBuilder contextBuilder,
            OutboxService outboxService,
            ObjectMapper objectMapper
    ) {
        this(imageGeneration, guardrails, validator, packager, storage, contextBuilder,
                outboxService, objectMapper, GenerationRequest.StubMode.NORMAL);
    }

    ArtworkPipeline(
            ImageGenerationPort imageGeneration,
            ContentGuardrails guardrails,
            SpriteValidator validator,
            AtlasPackager packager,
            ObjectStoragePort storage,
            ArtworkContextBuilder contextBuilder,
            OutboxService outboxService,
            ObjectMapper objectMapper,
            GenerationRequest.StubMode stubMode
    ) {
        this.imageGeneration = imageGeneration;
        this.guardrails = guardrails;
        this.validator = validator;
        this.packager = packager;
        this.storage = storage;
        this.contextBuilder = contextBuilder;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.stubMode = stubMode;
    }

    @Transactional
    public void enqueueInitial(Pet pet, Instant now) {
        Objects.requireNonNull(pet, "pet");
        if (PetArtwork.findByPet(pet).isPresent()) {
            return;
        }
        FrozenGenerationContext context = contextBuilder.buildContext(pet, now);
        String prompt = contextBuilder.buildPrompt(context);
        PetArtwork artwork = PetArtwork.createPending(pet, context, prompt, now);
        artwork.persist();
        LOG.infof("generation_started pet=%s artwork=%s", pet.id, artwork.id);
    }

    @Transactional
    public void processReadyWorkloads(Instant now) {
        List<PetArtwork> pending = PetArtwork.list(
                "generationStatus = ?1 OR (generationStatus = ?2 AND nextRetryAt <= ?3)",
                ArtGenerationStatus.GENERATING,
                ArtGenerationStatus.RETRY_WAIT,
                now
        );
        for (PetArtwork artwork : pending) {
            runGeneration(artwork, resolveReason(artwork), now);
        }
    }

    @Transactional
    public void runGeneration(PetArtwork artwork, ArtAttemptReason reason, Instant now) {
        Objects.requireNonNull(artwork, "artwork");
        Pet pet = artwork.pet;
        artwork.generationStatus = ArtGenerationStatus.GENERATING;
        artwork.nextRetryAt = null;
        artwork.updatedAt = now;

        String blocked = guardrails.blockedReason(artwork.promptPrivate);
        if (blocked != null) {
            markTechnicalFailure(artwork, blocked, now);
            return;
        }

        int attemptNo = PetArtworkAttempt.nextAttemptNo(artwork);
        int version = artwork.assetVersion + 1;
        String keyPrefix = ArtworkKeys.stagingPrefix(pet.id, version, attemptNo);

        GenerationRequest request = GenerationRequest.normal(
                artwork.promptPrivate,
                artwork.frozenContext(),
                artwork.seed == null ? pet.address.canonical : artwork.seed
        ).withStubMode(stubMode);

        try {
            GenerationResult generated = imageGeneration.generate(request);
            SpriteValidator.ValidationResult validation = validator.validateAtlas(generated.atlasPng());
            if (!validation.valid()) {
                recordAttempt(artwork, attemptNo, reason, false, validation.rejectionReason(), keyPrefix, now);
                LOG.warnf("validation_rejected artwork=%s reason=%s", artwork.id, validation.rejectionReason());
                markTechnicalFailure(artwork, "validation_" + validation.rejectionReason(), now);
                return;
            }

            packager.packageAtlas(generated.atlasPng(), validation.frames(), keyPrefix);
            recordAttempt(artwork, attemptNo, reason, true, null, keyPrefix, now);
            artwork.modelId = generated.modelId();
            artwork.assetVersion = version;
            artwork.lastFailureCode = null;
            artwork.lastFailureAt = null;
            artwork.technicalAttemptCount = 0;
            artwork.updatedAt = now;

            if (shouldAutoApprove(pet)) {
                approveInternal(artwork, keyPrefix, now);
            } else {
                artwork.generationStatus = ArtGenerationStatus.AWAITING_APPROVAL;
            }
        } catch (ImageGenerationException e) {
            recordAttempt(artwork, attemptNo, reason, false, e.code(), keyPrefix, now);
            LOG.warnf("provider_failure artwork=%s code=%s", artwork.id, e.code());
            markTechnicalFailure(artwork, e.code(), now);
        }
    }

    @Transactional
    public void approve(PetArtwork artwork, Instant now) {
        PetArtworkAttempt latest = latestValidAttempt(artwork);
        if (latest == null) {
            throw new IllegalStateException("Nenhuma tentativa válida para aprovar");
        }
        approveInternal(artwork, latest.storageKeyPrefix, now);
    }

    private void approveInternal(PetArtwork artwork, String stagingPrefix, Instant now) {
        Pet pet = artwork.pet;
        String approvedPrefix = ArtworkKeys.approvedPrefix(pet.id, artwork.assetVersion);
        List<String> keys = packager.objectKeys(stagingPrefix);
        storage.promote(keys, stagingPrefix, approvedPrefix);

        artwork.generationStatus = ArtGenerationStatus.APPROVED;
        pet.artworkStatus = ArtworkStatus.APPROVED;
        if (pet.bornAt != null) {
            pet.presentation = PetPresentation.CREATURE;
        }
        pet.updatedAt = now;
        artwork.updatedAt = now;
        emitArtworkReady(pet, artwork.assetVersion, now);
    }

    @Transactional
    public void requestVoluntaryRegeneration(PetArtwork artwork, Instant now) {
        if (artwork.voluntaryRegenUsed) {
            throw new ArtworkOperationException("regen_exhausted", "Regeneração voluntária já utilizada");
        }
        if (artwork.generationStatus == ArtGenerationStatus.APPROVED) {
            throw new ArtworkOperationException("already_approved", "Arte já aprovada");
        }
        if (artwork.generationStatus != ArtGenerationStatus.AWAITING_APPROVAL) {
            throw new ArtworkOperationException("not_ready", "Aguardando primeira geração válida");
        }
        if (latestValidAttempt(artwork) == null) {
            throw new ArtworkOperationException("not_ready", "Nenhuma geração válida disponível");
        }
        artwork.voluntaryRegenUsed = true;
        artwork.generationStatus = ArtGenerationStatus.GENERATING;
        artwork.updatedAt = now;
        runGeneration(artwork, ArtAttemptReason.VOLUNTARY_REGEN, now);
    }

    private void markTechnicalFailure(PetArtwork artwork, String code, Instant now) {
        artwork.technicalAttemptCount++;
        artwork.lastFailureCode = code;
        artwork.lastFailureAt = now;
        artwork.generationStatus = ArtGenerationStatus.RETRY_WAIT;
        artwork.nextRetryAt = ArtworkRetryPolicy.nextRetryAt(artwork.technicalAttemptCount, now);
        artwork.updatedAt = now;
        if (artwork.technicalAttemptCount >= ArtworkRetryPolicy.ALERT_THRESHOLD) {
            LOG.errorf("artwork_generation_alert artwork=%s attempts=%d code=%s",
                    artwork.id, artwork.technicalAttemptCount, code);
        }
    }

    private static ArtAttemptReason resolveReason(PetArtwork artwork) {
        if (artwork.voluntaryRegenUsed && artwork.generationStatus == ArtGenerationStatus.GENERATING) {
            return ArtAttemptReason.VOLUNTARY_REGEN;
        }
        if (artwork.technicalAttemptCount > 0) {
            return ArtAttemptReason.TECHNICAL_RETRY;
        }
        return ArtAttemptReason.INITIAL;
    }

    private static boolean shouldAutoApprove(Pet pet) {
        return !AccountAddressBinding.isActivelyBound(pet.creatorAccount, pet.address);
    }

    private static PetArtworkAttempt latestValidAttempt(PetArtwork artwork) {
        return PetArtworkAttempt.listByArtwork(artwork).stream()
                .filter(a -> a.valid)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private static void recordAttempt(
            PetArtwork artwork,
            int attemptNo,
            ArtAttemptReason reason,
            boolean valid,
            String rejection,
            String keyPrefix,
            Instant now
    ) {
        PetArtworkAttempt attempt = PetArtworkAttempt.create(
                artwork, attemptNo, reason, valid, rejection, keyPrefix, now
        );
        attempt.persist();
    }

    private void emitArtworkReady(Pet pet, int version, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("address", pet.address.canonical);
        payload.put("eventType", PET_ARTWORK_READY);
        payload.put("occurredAt", now.toString());
        payload.put("presentation", pet.presentation.name());
        payload.put("artworkVersion", version);
        payload.put("atlasUrl", ArtworkKeys.publicAtlasPath(pet.address.canonical, version));
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar PET_ARTWORK_READY", e);
        }
        UUID eventId = UUID.nameUUIDFromBytes(
                (PET_ARTWORK_READY + ":" + pet.id + ":v" + version).getBytes(StandardCharsets.UTF_8));
        outboxService.save(eventId, "Pet", pet.id.toString(), PET_ARTWORK_READY, json, null);
    }
}
