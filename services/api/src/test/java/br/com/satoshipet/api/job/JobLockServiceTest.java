package br.com.satoshipet.api.job;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class JobLockServiceTest {

    @Inject
    JobLockService jobLockService;

    @Inject
    DataSource dataSource;

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
    @Transactional
    void contençãoNaoMarcaTransacaoChamadoraComoRollback() {
        String occupiedJob = "lock-contention-" + UUID.randomUUID();
        String availableJob = "lock-after-contention-" + UUID.randomUUID();

        assertTrue(jobLockService.acquire(occupiedJob, "owner-A", Duration.ofMinutes(1)));
        assertFalse(jobLockService.acquire(occupiedJob, "owner-B", Duration.ofMinutes(1)));

        assertTrue(jobLockService.acquire(availableJob, "owner-B", Duration.ofMinutes(1)),
                "A transação chamadora deve continuar utilizável após uma contenção");

        jobLockService.release(occupiedJob, "owner-A");
        jobLockService.release(availableJob, "owner-B");
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
    void lockExpiradoPodeSerReadquirido() throws SQLException {
        String jobName = "lock-expired-" + UUID.randomUUID();

        assertTrue(jobLockService.acquire(jobName, "owner-old", Duration.ofMinutes(1)));
        expiraLock(jobName);

        boolean reacquired = jobLockService.acquire(jobName, "owner-new", Duration.ofMinutes(1));

        assertTrue(reacquired, "Lock expirado deve poder ser readquirido");

        // O owner anterior não pode liberar o lock já assumido por outra instância.
        jobLockService.release(jobName, "owner-old");
        assertFalse(jobLockService.acquire(jobName, "owner-observer", Duration.ofMinutes(1)),
                "A liberação de owner antigo não deve remover o lock readquirido");

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
    void lockExpiradoNaoPodeSerRenovado() throws SQLException {
        String jobName = "lock-renew-expired-" + UUID.randomUUID();

        assertTrue(jobLockService.acquire(jobName, "owner-old", Duration.ofMinutes(1)));
        expiraLock(jobName);

        assertFalse(jobLockService.renew(jobName, "owner-old", Duration.ofMinutes(5)),
                "Um owner cujo TTL expirou não pode reter o lock");
        assertTrue(jobLockService.acquire(jobName, "owner-new", Duration.ofMinutes(1)),
                "Outro owner deve assumir um lock expirado");

        jobLockService.release(jobName, "owner-new");
    }

    @Test
    void ownerIncorretoNaoLiberaLockAtivo() {
        String jobName = "lock-release-owner-" + UUID.randomUUID();

        assertTrue(jobLockService.acquire(jobName, "owner-real", Duration.ofMinutes(1)));

        jobLockService.release(jobName, "owner-incorreto");

        assertFalse(jobLockService.acquire(jobName, "owner-new", Duration.ofMinutes(1)),
                "Owner incorreto não deve remover o lock ativo");
        assertTrue(jobLockService.renew(jobName, "owner-real", Duration.ofMinutes(1)),
                "O owner correto deve continuar podendo renovar após a tentativa inválida");

        jobLockService.release(jobName, "owner-real");
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
        AtomicReference<String> winner = new AtomicReference<>();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(2);

        String owner1 = "owner-thread-1-" + UUID.randomUUID();
        String owner2 = "owner-thread-2-" + UUID.randomUUID();

        Thread t1 = new Thread(() -> {
            try {
                start.await();
                if (jobLockService.acquire(jobName, owner1, Duration.ofMinutes(1))) {
                    acquired.incrementAndGet();
                    winner.set(owner1);
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
                    winner.set(owner2);
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

        String acquiredBy = winner.get();
        assertNotNull(acquiredBy, "A thread vencedora deve ser identificada");
        String loser = owner1.equals(acquiredBy) ? owner2 : owner1;
        assertFalse(jobLockService.renew(jobName, loser, Duration.ofMinutes(1)),
                "A thread perdedora não pode renovar o lock da vencedora");
        assertFalse(jobLockService.acquire(jobName, loser, Duration.ofMinutes(1)),
                "A thread perdedora não pode adquirir novamente no mesmo ciclo");

        // Libera quem quer que tenha o lock
        jobLockService.release(jobName, acquiredBy);
        assertTrue(jobLockService.acquire(jobName, loser, Duration.ofMinutes(1)),
                "O lock deve ficar disponível somente após a liberação do vencedor");
        jobLockService.release(jobName, loser);
    }

    private void expiraLock(String jobName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE job_locks SET expires_at = ? WHERE job_name = ?")) {
            statement.setObject(1, Instant.now().minusSeconds(1));
            statement.setString(2, jobName);
            assertEquals(1, statement.executeUpdate(), "O lock de fixture deve existir");
        }
    }
}
