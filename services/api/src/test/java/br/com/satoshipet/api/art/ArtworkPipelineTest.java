package br.com.satoshipet.api.art;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.art.generation.GenerationRequest;
import br.com.satoshipet.api.art.generation.ImageGenerationPort;
import br.com.satoshipet.api.art.generation.StubImageGeneration;
import br.com.satoshipet.api.outbox.OutboxEvent;
import br.com.satoshipet.api.outbox.OutboxService;
import br.com.satoshipet.api.pet.ArtworkStatus;
import br.com.satoshipet.api.pet.Pet;
import br.com.satoshipet.api.pet.PetPresentation;
import br.com.satoshipet.api.storage.NoOpObjectStorage;
import br.com.satoshipet.api.storage.ObjectStoragePort;
import br.com.satoshipet.api.storage.StorageNamespace;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pipeline de arte: geração, validação, aprovação e regeneração (CA-036..039). */
@QuarkusTest
class ArtworkPipelineTest {

    private static final Instant NOW = Instant.parse("2026-09-14T15:00:00Z");

    @Inject
    OutboxService outboxService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ContentGuardrails guardrails;

    @Inject
    SpriteValidator validator;

    @Inject
    AtlasPackager packager;

    @Inject
    ArtworkContextBuilder contextBuilder;

    private ObjectStoragePort storage;
    private ArtworkPipeline pipeline;

    @BeforeEach
    void setUp() {
        storage = new NoOpObjectStorage();
        packager = new AtlasPackager(storage);
        pipeline = new ArtworkPipeline(
                new StubImageGeneration(),
                guardrails,
                validator,
                packager,
                storage,
                contextBuilder,
                outboxService,
                objectMapper,
                GenerationRequest.StubMode.NORMAL
        );
    }

    @Test
    void geraAtlasCoerenteComMesmaPaleta() throws Exception {
        byte[] atlas = StubImageGeneration.buildValidAtlas("seed-test");
        SpriteValidator.ValidationResult result = validator.validateAtlas(atlas);
        assertTrue(result.valid());
        assertEquals(14, result.frames().size());
    }

    @Test
    void enqueueInicialEhIdempotente() {
        UUID petId = persistBornPet("enqueue");
        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            pipeline.enqueueInitial(pet, NOW);
            pipeline.enqueueInitial(pet, NOW);
        });
        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            assertEquals(1, PetArtwork.count("pet", pet));
        });
    }

    @Test
    void criadorVinculadoAguardaAprovacao() {
        UUID petId = persistBornPet("await");
        runPipeline(petId);
        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            PetArtwork artwork = PetArtwork.findByPet(pet).orElseThrow();
            assertEquals(ArtGenerationStatus.AWAITING_APPROVAL, artwork.generationStatus);
            assertEquals(ArtworkStatus.PENDING, pet.artworkStatus);
            assertEquals(1, PetArtworkAttempt.listByArtwork(artwork).size());
            assertFalse(artwork.voluntaryRegenUsed);
        });
    }

    @Test
    void aprovarPromoveAssetsEEmitReady() {
        UUID petId = persistBornPet("approve");
        runPipeline(petId);
        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            PetArtwork artwork = PetArtwork.findByPet(pet).orElseThrow();
            pipeline.approve(artwork, NOW);
        });
        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            PetArtwork artwork = PetArtwork.findByPet(pet).orElseThrow();
            assertEquals(ArtGenerationStatus.APPROVED, artwork.generationStatus);
            assertEquals(ArtworkStatus.APPROVED, pet.artworkStatus);
            assertEquals(PetPresentation.CREATURE, pet.presentation);
            String key = ArtworkKeys.approvedPrefix(pet.id, artwork.assetVersion) + "/atlas.png";
            assertTrue(storage.exists(StorageNamespace.APPROVED, key));
            List<OutboxEvent> ready = OutboxEvent.list("eventType", ArtworkPipeline.PET_ARTWORK_READY);
            assertFalse(ready.isEmpty());
        });
    }

    @Test
    void regeneracaoVoluntariaLimitadaEUltimaValidaPermanece() {
        UUID petId = persistBornPet("regen");
        runPipeline(petId);
        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            PetArtwork artwork = PetArtwork.findByPet(pet).orElseThrow();
            pipeline.requestVoluntaryRegeneration(artwork, NOW);
        });
        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            PetArtwork artwork = PetArtwork.findByPet(pet).orElseThrow();
            assertTrue(artwork.voluntaryRegenUsed);
            assertEquals(ArtGenerationStatus.AWAITING_APPROVAL, artwork.generationStatus);
            assertEquals(2, PetArtworkAttempt.listByArtwork(artwork).size());
        });
    }

    @Test
    void falhaTecnicaNaoConsomeSorteio() {
        UUID petId = persistBornPet("fail");
        ArtworkPipeline failing = new ArtworkPipeline(
                request -> {
                    throw new br.com.satoshipet.api.art.generation.ImageGenerationException(
                            "provider_failure", "falha simulada");
                },
                guardrails,
                validator,
                packager,
                storage,
                contextBuilder,
                outboxService,
                objectMapper,
                GenerationRequest.StubMode.NORMAL
        );
        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            failing.enqueueInitial(pet, NOW);
            PetArtwork artwork = PetArtwork.findByPet(pet).orElseThrow();
            failing.runGeneration(artwork, ArtAttemptReason.INITIAL, NOW);
        });
        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            PetArtwork artwork = PetArtwork.findByPet(pet).orElseThrow();
            assertEquals(ArtGenerationStatus.RETRY_WAIT, artwork.generationStatus);
            assertFalse(artwork.voluntaryRegenUsed);
            assertEquals(ArtworkStatus.PENDING, pet.artworkStatus);
        });
    }

    @Test
    void preservaPrimeiraGeracaoValidaAoReabrir() {
        UUID petId = persistBornPet("ca038");
        runPipeline(petId);
        Integer attemptNo = QuarkusTransaction.requiringNew().call(() -> {
            Pet pet = Pet.findById(petId);
            PetArtwork artwork = PetArtwork.findByPet(pet).orElseThrow();
            PetArtworkAttempt attempt = PetArtworkAttempt.listByArtwork(artwork).getFirst();
            return attempt.attemptNo;
        });
        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            PetArtwork artwork = PetArtwork.findByPet(pet).orElseThrow();
            assertEquals(ArtGenerationStatus.AWAITING_APPROVAL, artwork.generationStatus);
            assertEquals(attemptNo, PetArtworkAttempt.listByArtwork(artwork).getFirst().attemptNo);
        });
    }

    @Test
    void timezonePosteriorNaoAlteraArteAprovada() {
        UUID petId = QuarkusTransaction.requiringNew().call(() -> {
            Fixture fixture = persistBornPetFixture("ca040");
            fixture.binding.unbind(NOW);
            return fixture.pet.id;
        });
        runPipeline(petId);
        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            PetArtwork artwork = PetArtwork.findByPet(pet).orElseThrow();
            int versionBefore = artwork.assetVersion;
            pet.creatorAccount.timezone = "Europe/Lisbon";
            pet.creatorAccount.persist();
            assertEquals(versionBefore, artwork.assetVersion);
            assertEquals(ArtGenerationStatus.APPROVED, artwork.generationStatus);
        });
    }

    @Test
    void autoAprovaQuandoCriadorNaoEstaVinculado() {
        UUID petId = QuarkusTransaction.requiringNew().call(() -> {
            Fixture fixture = persistBornPetFixture("auto");
            fixture.binding.unbind(NOW);
            return fixture.pet.id;
        });
        runPipeline(petId);
        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            PetArtwork artwork = PetArtwork.findByPet(pet).orElseThrow();
            assertEquals(ArtGenerationStatus.APPROVED, artwork.generationStatus);
            assertEquals(ArtworkStatus.APPROVED, pet.artworkStatus);
        });
    }

    private void runPipeline(UUID petId) {
        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            pipeline.enqueueInitial(pet, NOW);
            PetArtwork artwork = PetArtwork.findByPet(pet).orElseThrow();
            pipeline.runGeneration(artwork, ArtAttemptReason.INITIAL, NOW);
        });
    }

    private static UUID persistBornPet(String marker) {
        return QuarkusTransaction.requiringNew().call(() -> persistBornPetFixture(marker).pet.id);
    }

    private static Fixture persistBornPetFixture(String marker) {
        Address address = Address.create(uniqueCanonical(marker), NOW);
        address.persist();
        Account account = Account.create(
                marker + "-" + UUID.randomUUID() + "@test.com",
                "America/Sao_Paulo",
                "pt-BR",
                NOW
        );
        account.persist();
        AccountAddressBinding binding = AccountAddressBinding.create(account, address, true, NOW);
        binding.persist();
        Pet pet = Pet.create(address, account, "Arte-" + marker, NOW);
        pet.bornAt = NOW;
        pet.artworkStatus = ArtworkStatus.PENDING;
        pet.persist();
        return new Fixture(pet, account, address, binding);
    }

    private static String uniqueCanonical(String marker) {
        return "bcrt1q" + marker.replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private record Fixture(Pet pet, Account account, Address address, AccountAddressBinding binding) {
    }
}
