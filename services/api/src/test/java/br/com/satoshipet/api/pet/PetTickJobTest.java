package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.job.JobLockService;
import br.com.satoshipet.api.pet.engine.EmotionalState;
import java.util.concurrent.atomic.AtomicInteger;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tique de relógio do servidor com o app fechado (PET-18, CA-025).
 */
@QuarkusTest
class PetTickJobTest {

    @Inject
    PetTickJob job;

    @Test
    void lockNaoAdquiridoNaoChamaTick() {
        JobLockService locks = mock(JobLockService.class);
        PetLifecyclePort lifecycle = mock(PetLifecyclePort.class);
        when(locks.acquire(eq("pet-tick"), any(), eq(Duration.ofSeconds(120)))).thenReturn(false);

        PetTickJob isolated = new PetTickJob(locks, lifecycle);
        isolated.tick();

        verify(locks).acquire(eq("pet-tick"), any(), eq(Duration.ofSeconds(120)));
        verify(lifecycle, never()).tick(any(), any());
        verify(locks, never()).release(any(), any());
    }

    @Test
    void concessaoPerdidaInterrompeAntesDeQualquerTick() {
        createDepletedPet(Instant.now().minus(Duration.ofHours(10)));
        AtomicInteger checked = new AtomicInteger();
        AtomicInteger ticked = new AtomicInteger();
        JobLockService locks = new JobLockService(null, null) {
            @Override public boolean runWhileOwned(String name, String owner, Duration ttl, Runnable work) {
                checked.incrementAndGet();
                return false;
            }
        };
        PetLifecyclePort lifecycle = new LoggingPetLifecyclePort() {
            @Override public void tick(UUID petId, Instant now) { ticked.incrementAndGet(); }
        };

        new PetTickJob(locks, lifecycle).runTickCycle("lost-owner");

        assertEquals(1, checked.get());
        assertEquals(0, ticked.get());
    }

    @Test
    void ca025TickComAppFechadoAtualizaParaPensando() {
        Instant depletedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS).minus(Duration.ofHours(10));
        UUID petId = createDepletedPet(depletedAt);

        job.tick();

        QuarkusTransaction.requiringNew().run(() -> {
            Pet updated = Pet.findById(petId);
            assertEquals(EmotionalState.PENSANDO, updated.emotionalState);
            assertEquals(depletedAt, updated.reserveDepletedAt);
        });
    }

    private UUID createDepletedPet(Instant depletedAt) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Address address = Address.create(
                    "bcrt1qtick" + UUID.randomUUID().toString().replace("-", ""), depletedAt);
            address.persist();
            Account account = Account.create(
                    "tick-" + UUID.randomUUID() + "@test.com",
                    "America/Sao_Paulo",
                    "pt-BR",
                    depletedAt
            );
            account.persist();
            AccountAddressBinding.create(account, address, true, depletedAt).persist();
            Pet pet = Pet.create(address, account, "Tick", depletedAt);
            pet.presentation = PetPresentation.CREATURE;
            pet.bornAt = depletedAt;
            pet.reserveHours = BigDecimal.ZERO.setScale(10);
            pet.lastEvaluatedAt = depletedAt;
            pet.reserveDepletedAt = depletedAt;
            pet.emotionalState = EmotionalState.ALIMENTADO;
            pet.persist();

            return pet.id;
        });

    }
}
