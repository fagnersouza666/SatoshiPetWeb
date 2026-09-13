package br.com.satoshipet.api.job;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class JobLockServiceTest {

    @Inject
    JobLockService jobLockService;

    @Test
    void adquireLockQuandoNaoExiste() {
        String jobName = "lock-acquire-" + UUID.randomUUID();
        String ownerId = "owner-1";

        boolean acquired = jobLockService.acquire(jobName, ownerId, Duration.ofMinutes(1));

        assertTrue(acquired, "Deve adquirir lock em job novo");

        jobLockService.release(jobName, ownerId);
    }

    @Test
    void naoAdquireLockDetidoPorOutroOwner() {
        String jobName = "lock-exclusive-" + UUID.randomUUID();

        boolean firstAcquired = jobLockService.acquire(jobName, "owner-A", Duration.ofMinutes(1));
        boolean secondAcquired = jobLockService.acquire(jobName, "owner-B", Duration.ofMinutes(1));

        assertTrue(firstAcquired, "owner-A deve adquirir");
        assertFalse(secondAcquired, "owner-B não deve adquirir lock já detido por owner-A");

        jobLockService.release(jobName, "owner-A");
    }

    @Test
    void liberaLockEPermiteNovaAquisicao() {
        String jobName = "lock-release-" + UUID.randomUUID();

        jobLockService.acquire(jobName, "owner-1", Duration.ofMinutes(1));
        jobLockService.release(jobName, "owner-1");

        boolean reacquired = jobLockService.acquire(jobName, "owner-2", Duration.ofMinutes(1));

        assertTrue(reacquired, "Deve adquirir após liberação");

        jobLockService.release(jobName, "owner-2");
    }

    @Test
    void lockExpiradoPodeSerReadquirido() {
        String jobName = "lock-expired-" + UUID.randomUUID();

        // Adquire com TTL muito curto (1 ms)
        jobLockService.acquire(jobName, "owner-old", Duration.ofMillis(1));

        // Aguarda expiração
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}

        boolean reacquired = jobLockService.acquire(jobName, "owner-new", Duration.ofMinutes(1));

        assertTrue(reacquired, "Lock expirado deve poder ser readquirido");

        jobLockService.release(jobName, "owner-new");
    }

    @Test
    void renovaLockDetidoPeloMesmoOwner() {
        String jobName = "lock-renew-" + UUID.randomUUID();

        jobLockService.acquire(jobName, "owner-1", Duration.ofSeconds(10));
        boolean renewed = jobLockService.renew(jobName, "owner-1", Duration.ofMinutes(5));

        assertTrue(renewed, "Deve renovar lock do mesmo owner");

        jobLockService.release(jobName, "owner-1");
    }

    @Test
    void naoRenovaLockDeOutroOwner() {
        String jobName = "lock-renew-owner-" + UUID.randomUUID();

        assertTrue(jobLockService.acquire(jobName, "owner-1", Duration.ofMinutes(1)));

        assertFalse(jobLockService.renew(jobName, "owner-2", Duration.ofMinutes(5)),
                "Somente o owner atual pode renovar o lock");
        assertFalse(jobLockService.acquire(jobName, "owner-2", Duration.ofMillis(20)),
                "A tentativa de renovação de outro owner não deve liberar o lock");

        jobLockService.release(jobName, "owner-1");
    }

    @Test
    void lockExpiradoNaoPodeSerRenovado() throws InterruptedException {
        String jobName = "lock-renew-expired-" + UUID.randomUUID();

        assertTrue(jobLockService.acquire(jobName, "owner-old", Duration.ofMillis(1)));
        Thread.sleep(50);

        assertFalse(jobLockService.renew(jobName, "owner-old", Duration.ofMinutes(5)),
                "Um owner cujo TTL expirou não pode reter o lock");
        assertTrue(jobLockService.acquire(jobName, "owner-new", Duration.ofMinutes(1)),
                "Outro owner deve assumir um lock expirado");

        jobLockService.release(jobName, "owner-new");
    }

    @Test
    void rejeitaTtlNaoPositivo() {
        String jobName = "lock-invalid-ttl-" + UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> jobLockService.acquire(jobName, "owner-1", Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> jobLockService.renew(jobName, "owner-1", Duration.ofSeconds(-1)));
    }

    @Test
    void releaseIdempotente() {
        String jobName = "lock-idempotent-release-" + UUID.randomUUID();

        jobLockService.acquire(jobName, "owner-1", Duration.ofMinutes(1));
        jobLockService.release(jobName, "owner-1");

        // Segunda chamada não deve lançar exceção
        jobLockService.release(jobName, "owner-1");
    }

    /**
     * Dois threads competem pelo mesmo lock.
     * Apenas um deve adquirir com sucesso.
     */
    @Test
    void apenasUmaThreadAdquireLockConcorrente() throws InterruptedException {
        String jobName = "lock-concurrent-" + UUID.randomUUID();
        AtomicInteger acquired = new AtomicInteger(0);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(2);

        String owner1 = "owner-thread-1-" + UUID.randomUUID();
        String owner2 = "owner-thread-2-" + UUID.randomUUID();

        Thread t1 = new Thread(() -> {
            try {
                start.await();
                if (jobLockService.acquire(jobName, owner1, Duration.ofMinutes(1))) {
                    acquired.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                start.await();
                if (jobLockService.acquire(jobName, owner2, Duration.ofMinutes(1))) {
                    acquired.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        t1.start();
        t2.start();
        start.countDown();

        boolean finished = done.await(10, TimeUnit.SECONDS);

        assertTrue(finished, "Ambas as threads devem completar dentro do timeout");
        assertEquals(1, acquired.get(),
                "Exatamente uma thread deve ter adquirido o lock");

        // Libera quem quer que tenha o lock
        jobLockService.release(jobName, owner1);
        jobLockService.release(jobName, owner2);
    }
}
