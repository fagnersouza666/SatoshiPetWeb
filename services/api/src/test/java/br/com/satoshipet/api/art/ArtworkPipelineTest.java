package br.com.satoshipet.api.art;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.art.generation.GenerationRequest;
import br.com.satoshipet.api.art.generation.ImageGenerationPort;
import br.com.satoshipet.api.art.generation.StubImageGeneration;
import br.com.satoshipet.api.btc.AddressMonitorState;
import br.com.satoshipet.api.outbox.OutboxEvent;
import br.com.satoshipet.api.outbox.OutboxService;
import br.com.satoshipet.api.pet.ArtworkStatus;
import br.com.satoshipet.api.pet.Pet;
import br.com.satoshipet.api.pet.PetPresentation;
import br.com.satoshipet.api.pet.PetLifecyclePort;
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
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Inject
    PetLifecyclePort petLifecycle;

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
                petLifecycle,
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
    void concessaoPerdidaInterrompeSemExecutarNemPularParaOutraArte() {
        UUID firstPet = persistBornPet("lease-first");
        UUID secondPet = persistBornPet("lease-second");
        QuarkusTransaction.requiringNew().run(() -> {
            pipeline.enqueueInitial(Pet.findById(firstPet), NOW);
            pipeline.enqueueInitial(Pet.findById(secondPet), NOW);
        });
        var attempts = new java.util.concurrent.atomic.AtomicInteger();

        pipeline.processReadyWorkloads(NOW, work -> {
            attempts.incrementAndGet();
            return false;
        });

        assertEquals(1, attempts.get());
        QuarkusTransaction.requiringNew().run(() -> {
            for (UUID id : List.of(firstPet, secondPet)) {
                PetArtwork artwork = PetArtwork.findByPetId(id).orElseThrow();
                assertEquals(ArtGenerationStatus.GENERATING, artwork.generationStatus);
                assertEquals(0, PetArtworkAttempt.listByArtwork(artwork).size());
            }
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
            assertEquals(PetPresentation.EGG, pet.presentation);
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
                petLifecycle,
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

    @Test
    void saidaDoCriadorDepoisDaGeracaoAprovaSemGerarNovamente() {
        UUID petId = persistBornPet("creator-leaves");
        runPipeline(petId);
        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            AccountAddressBinding.findActivePrimary(pet.creatorAccount).orElseThrow().unbind(NOW);
        });

        pipeline.processReadyWorkloads(NOW.plusSeconds(30));

        QuarkusTransaction.requiringNew().run(() -> {
            PetArtwork artwork = PetArtwork.findByPetId(petId).orElseThrow();
            assertEquals(ArtGenerationStatus.APPROVED, artwork.generationStatus);
            assertEquals(1, artwork.assetVersion);
            assertEquals(1, PetArtworkAttempt.listByArtwork(artwork).size());
        });
    }

    @Test
    void reaprovacaoPreservaOvoEVersaoSemNovoEvento() {
        UUID petId = persistBornPet("reapprove-egg");
        runPipeline(petId);
        QuarkusTransaction.requiringNew().run(() -> {
            PetArtwork artwork = PetArtwork.findByPetId(petId).orElseThrow();
            pipeline.approve(artwork, NOW);
            artwork.pet.presentation = PetPresentation.EGG;
            artwork.pet.zeroBalanceSince = NOW;
            artwork.pet.lastReturnedToEggAt = NOW.plus(Duration.ofHours(24));
        });
        long before = QuarkusTransaction.requiringNew().call(() -> OutboxEvent.count(
                "aggregateId = ?1 AND eventType = ?2", petId.toString(), ArtworkPipeline.PET_ARTWORK_READY));

        QuarkusTransaction.requiringNew().run(() ->
                pipeline.approve(PetArtwork.findByPetId(petId).orElseThrow(), NOW.plus(Duration.ofHours(25))));

        QuarkusTransaction.requiringNew().run(() -> {
            PetArtwork artwork = PetArtwork.findByPetId(petId).orElseThrow();
            assertEquals(PetPresentation.EGG, artwork.pet.presentation);
            assertEquals(1, artwork.assetVersion);
            assertEquals(before, OutboxEvent.count("aggregateId = ?1 AND eventType = ?2",
                    petId.toString(), ArtworkPipeline.PET_ARTWORK_READY));
        });
    }

    @Test
    void primeiraAprovacaoComSaldoDesconhecidoNaoRetiraDoOvo() {
        UUID petId = persistBornPet("approve-unknown");
        runPipeline(petId);
        QuarkusTransaction.requiringNew().run(() -> {
            PetArtwork artwork = PetArtwork.findByPetId(petId).orElseThrow();
            // Não há snapshot reconciliado: bornAt sozinho não autoriza reaparecer.
            pipeline.approve(artwork, NOW.plus(Duration.ofHours(25)));
        });
        QuarkusTransaction.requiringNew().run(() -> {
            PetArtwork artwork = PetArtwork.findByPetId(petId).orElseThrow();
            assertEquals(ArtGenerationStatus.APPROVED, artwork.generationStatus);
            assertEquals(PetPresentation.EGG, artwork.pet.presentation);
        });
    }

    @Test
    void primeiraAprovacaoComSaldoPositivoConhecidoApresentaCriatura() {
        UUID petId = persistBornPet("approve-positive");
        runPipeline(petId);
        QuarkusTransaction.requiringNew().run(() -> {
            PetArtwork artwork = PetArtwork.findByPetId(petId).orElseThrow();
            AddressMonitorState state = AddressMonitorState.loadOrCreate(artwork.pet.address, NOW);
            state.recordBalance(5_000L, 0L, NOW);
            pipeline.approve(artwork, NOW);
        });
        QuarkusTransaction.requiringNew().run(() -> {
            PetArtwork artwork = PetArtwork.findByPetId(petId).orElseThrow();
            assertEquals(PetPresentation.CREATURE, artwork.pet.presentation);
            List<OutboxEvent> events = OutboxEvent.list("aggregateId = ?1 AND eventType = ?2",
                    petId.toString(), ArtworkPipeline.PET_ARTWORK_READY);
            assertEquals(1, events.size());
            assertTrue(events.getFirst().payload.contains("\"presentation\":\"CREATURE\""));
        });
    }

    @Test
    void primeiraAprovacaoTardiaComSaldoZeroPreservaOvo() {
        UUID petId = persistBornPet("approve-zero");
        runPipeline(petId);
        Instant late = NOW.plus(Duration.ofHours(25));
        QuarkusTransaction.requiringNew().run(() -> {
            PetArtwork artwork = PetArtwork.findByPetId(petId).orElseThrow();
            artwork.pet.zeroBalanceSince = NOW;
            AddressMonitorState state = AddressMonitorState.loadOrCreate(artwork.pet.address, late);
            state.recordBalance(0L, 0L, late);
            pipeline.approve(artwork, late);
        });
        QuarkusTransaction.requiringNew().run(() -> {
            PetArtwork artwork = PetArtwork.findByPetId(petId).orElseThrow();
            assertEquals(ArtGenerationStatus.APPROVED, artwork.generationStatus);
            assertEquals(PetPresentation.EGG, artwork.pet.presentation);
        });
    }

    @Test
    void primeiraAprovacaoNaoUsaSaldoPositivoDeProvedorIndisponivel() {
        UUID petId = persistBornPet("approve-unavailable");
        runPipeline(petId);
        QuarkusTransaction.requiringNew().run(() -> {
            PetArtwork artwork = PetArtwork.findByPetId(petId).orElseThrow();
            AddressMonitorState state = AddressMonitorState.loadOrCreate(artwork.pet.address, NOW);
            state.recordBalance(5_000L, 0L, NOW);
            state.providerAvailable = false;
            pipeline.approve(artwork, NOW.plusSeconds(30));
        });
        QuarkusTransaction.requiringNew().run(() -> {
            PetArtwork artwork = PetArtwork.findByPetId(petId).orElseThrow();
            assertEquals(PetPresentation.EGG, artwork.pet.presentation);
        });
    }

    @Test
    void aprovacaoAntesDeGeracaoValidaRetornaErroDeDominio() {
        UUID petId = persistBornPet("approve-early");
        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            pipeline.enqueueInitial(pet, NOW);
            ArtworkOperationException failure = assertThrows(ArtworkOperationException.class,
                    () -> pipeline.approve(PetArtwork.findByPet(pet).orElseThrow(), NOW));
            assertEquals("not_ready", failure.code());
        });
    }

    @Test
    void workloadAntigoNaoGeraOutraVersaoDepoisDaAprovacao() {
        UUID petId = persistBornPet("stale-workload");
        runPipeline(petId);
        QuarkusTransaction.requiringNew().run(() ->
                pipeline.approve(PetArtwork.findByPetId(petId).orElseThrow(), NOW));

        QuarkusTransaction.requiringNew().run(() -> pipeline.runGeneration(
                PetArtwork.findByPetId(petId).orElseThrow(), ArtAttemptReason.INITIAL, NOW.plusSeconds(30)));

        QuarkusTransaction.requiringNew().run(() -> {
            PetArtwork artwork = PetArtwork.findByPetId(petId).orElseThrow();
            assertEquals(ArtGenerationStatus.APPROVED, artwork.generationStatus);
            assertEquals(1, artwork.assetVersion);
            assertEquals(1, PetArtworkAttempt.listByArtwork(artwork).size());
        });
    }

    @Test
    void aprovacaoRecarregaVersaoSeRegeneracaoConcorrenteJaDetemPet() throws Exception {
        UUID petId = persistBornPet("concurrent-regen");
        runPipeline(petId);
        CountDownLatch generationStarted = new CountDownLatch(1);
        CountDownLatch releaseGeneration = new CountDownLatch(1);
        CountDownLatch staleApprovalLoaded = new CountDownLatch(1);
        ImageGenerationPort delayed = request -> {
            generationStarted.countDown();
            await(releaseGeneration);
            return new StubImageGeneration().generate(request);
        };
        ArtworkPipeline regenerating = new ArtworkPipeline(delayed, guardrails, validator, packager,
                storage, contextBuilder, outboxService, objectMapper, petLifecycle, GenerationRequest.StubMode.NORMAL);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var regeneration = executor.submit(() -> QuarkusTransaction.requiringNew().run(() ->
                    regenerating.requestVoluntaryRegeneration(PetArtwork.findByPetId(petId).orElseThrow(), NOW)));
            assertTrue(generationStarted.await(10, TimeUnit.SECONDS));
            var approval = executor.submit(() -> QuarkusTransaction.requiringNew().run(() -> {
                PetArtwork stale = PetArtwork.findByPetId(petId).orElseThrow();
                staleApprovalLoaded.countDown();
                pipeline.approve(stale, NOW.plusSeconds(1));
            }));
            try {
                assertTrue(staleApprovalLoaded.await(10, TimeUnit.SECONDS));
            } finally {
                releaseGeneration.countDown();
            }
            regeneration.get(15, TimeUnit.SECONDS);
            approval.get(15, TimeUnit.SECONDS);
        } finally {
            releaseGeneration.countDown();
        }
        QuarkusTransaction.requiringNew().run(() -> {
            PetArtwork artwork = PetArtwork.findByPetId(petId).orElseThrow();
            assertEquals(ArtGenerationStatus.APPROVED, artwork.generationStatus);
            assertEquals(2, artwork.assetVersion);
            assertTrue(artwork.voluntaryRegenUsed);
            assertEquals(2, PetArtworkAttempt.listByArtwork(artwork).size());
        });
    }

    @Test
    void regeneracaoComLeituraAntigaNaoSubstituiAprovacaoConcorrente() throws Exception {
        UUID petId = persistBornPet("concurrent-approve");
        CountDownLatch promotionStarted = new CountDownLatch(1);
        CountDownLatch releasePromotion = new CountDownLatch(1);
        CountDownLatch staleRegenerationLoaded = new CountDownLatch(1);
        ObjectStoragePort delayedStorage = new NoOpObjectStorage() {
            @Override
            public void promote(List<String> keys, String stagingPrefix, String approvedPrefix) {
                promotionStarted.countDown();
                await(releasePromotion);
                super.promote(keys, stagingPrefix, approvedPrefix);
            }
        };
        ArtworkPipeline approving = new ArtworkPipeline(new StubImageGeneration(), guardrails, validator,
                new AtlasPackager(delayedStorage), delayedStorage, contextBuilder, outboxService,
                objectMapper, petLifecycle, GenerationRequest.StubMode.NORMAL);
        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            approving.enqueueInitial(pet, NOW);
            approving.runGeneration(PetArtwork.findByPet(pet).orElseThrow(), ArtAttemptReason.INITIAL, NOW);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var approval = executor.submit(() -> QuarkusTransaction.requiringNew().run(() ->
                    approving.approve(PetArtwork.findByPetId(petId).orElseThrow(), NOW)));
            assertTrue(promotionStarted.await(10, TimeUnit.SECONDS));
            var regeneration = executor.submit(() -> QuarkusTransaction.requiringNew().call(() -> {
                PetArtwork stale = PetArtwork.findByPetId(petId).orElseThrow();
                staleRegenerationLoaded.countDown();
                try {
                    pipeline.requestVoluntaryRegeneration(stale, NOW.plusSeconds(1));
                    return "unexpected_success";
                } catch (ArtworkOperationException failure) {
                    return failure.code();
                }
            }));
            try {
                assertTrue(staleRegenerationLoaded.await(10, TimeUnit.SECONDS));
            } finally {
                releasePromotion.countDown();
            }
            approval.get(15, TimeUnit.SECONDS);
            assertEquals("already_approved", regeneration.get(15, TimeUnit.SECONDS));
        } finally {
            releasePromotion.countDown();
        }
        QuarkusTransaction.requiringNew().run(() -> {
            PetArtwork artwork = PetArtwork.findByPetId(petId).orElseThrow();
            assertEquals(ArtGenerationStatus.APPROVED, artwork.generationStatus);
            assertEquals(1, artwork.assetVersion);
            assertFalse(artwork.voluntaryRegenUsed);
            assertEquals(1, PetArtworkAttempt.listByArtwork(artwork).size());
        });
    }

    @Test
    void contaCriadoraExcluidaNaoImpedeCongelarContextoEAprovar() {
        UUID petId = persistBornPet("deleted-creator");
        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            pet.creatorAccount = null;
        });

        runPipeline(petId);

        QuarkusTransaction.requiringNew().run(() -> {
            PetArtwork artwork = PetArtwork.findByPetId(petId).orElseThrow();
            assertEquals(ArtGenerationStatus.APPROVED, artwork.generationStatus);
            assertEquals("Etc/UTC", artwork.frozenContext().timezone());
            ArtworkService service = new ArtworkService(pipeline, storage);
            assertFalse(service.artworkInfo(artwork.pet, null).orElseThrow().canApprove());
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new AssertionError("A geração concorrente não foi liberada");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
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
