package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.job.JobLockService;
import br.com.satoshipet.api.pet.engine.EmotionalState;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
    @Transactional
    void ca025TickComAppFechadoAtualizaParaPensando() {
        Instant depletedAt = Instant.now().minus(Duration.ofHours(10));
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

        job.runTickCycle();

        Pet updated = Pet.findById(pet.id);
        assertEquals(EmotionalState.PENSANDO, updated.emotionalState);
        assertEquals(depletedAt, updated.reserveDepletedAt);
    }
}
