package br.com.satoshipet.api.job;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JobLeaseWorkTest {
    @Inject JobLockService locks;
    @Inject DataSource dataSource;

    @Test
    void ownerAntigoNaoExecutaDepoisQueOutroAssume() throws Exception {
        String name = "lease-fenced-" + UUID.randomUUID();
        assertTrue(locks.acquire(name, "old", Duration.ofMinutes(1)));
        expire(name);
        assertTrue(locks.acquire(name, "new", Duration.ofMinutes(1)));
        AtomicInteger effects = new AtomicInteger();

        assertFalse(locks.runWhileOwned(name, "old", Duration.ofSeconds(2), effects::incrementAndGet));
        assertEquals(0, effects.get());
        assertTrue(locks.runWhileOwned(name, "new", Duration.ofSeconds(2), effects::incrementAndGet));
        assertEquals(1, effects.get());
        locks.release(name, "new");
    }

    @Test
    void leaseExpiradaOuAusenteNaoExecutaTrabalho() throws Exception {
        String name = "lease-expired-work-" + UUID.randomUUID();
        AtomicInteger effects = new AtomicInteger();
        assertFalse(locks.runWhileOwned(name, "old", Duration.ofSeconds(1), effects::incrementAndGet));
        assertTrue(locks.acquire(name, "old", Duration.ofMinutes(1)));
        expire(name);
        assertFalse(locks.runWhileOwned(name, "old", Duration.ofSeconds(1), effects::incrementAndGet));
        assertEquals(0, effects.get());
        locks.release(name, "old");
    }

    @Test
    void trabalhoLongoImpedeAssuncaoMesmoDepoisDoTtl() throws Exception {
        String name = "lease-long-work-" + UUID.randomUUID();
        assertTrue(locks.acquire(name, "old", Duration.ofSeconds(1)));
        CountDownLatch working = new CountDownLatch(1);
        CountDownLatch allowFinish = new CountDownLatch(1);
        CountDownLatch contenderStarted = new CountDownLatch(1);
        CountDownLatch contenderDone = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> acquired = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                assertTrue(locks.runWhileOwned(name, "old", Duration.ofMillis(100), () -> {
                    working.countDown();
                    await(allowFinish);
                }));
            } catch (Throwable e) { failure.set(e); }
        });
        Thread contender = new Thread(() -> {
            try {
                contenderStarted.countDown();
                acquired.set(locks.acquire(name, "new", Duration.ofMinutes(1)));
            } catch (Throwable e) { failure.set(e); }
            finally { contenderDone.countDown(); }
        });
        try {
            worker.start();
            assertTrue(working.await(5, TimeUnit.SECONDS));
            // Passa o TTL real enquanto a unidade de trabalho permanece aberta.
            assertFalse(allowFinish.await(1_200, TimeUnit.MILLISECONDS));
            contender.start();
            assertTrue(contenderStarted.await(5, TimeUnit.SECONDS));
            assertFalse(contenderDone.await(200, TimeUnit.MILLISECONDS),
                    "A aquisição concorrente deve aguardar a transação da unidade de trabalho");
        } finally {
            allowFinish.countDown();
            worker.join(5_000);
            contender.join(5_000);
        }
        assertFalse(worker.isAlive());
        assertFalse(contender.isAlive());
        assertNull(failure.get());
        assertEquals(Boolean.FALSE, acquired.get(), "A renovação no commit preserva o owner atual");
        locks.release(name, "old");
    }

    @Test
    void falhaReverteEfeitosEPermiteRetomarUnidade() {
        String name = "lease-rollback-" + UUID.randomUUID();
        String effect = "lease-effect-" + UUID.randomUUID();
        assertTrue(locks.acquire(name, "owner", Duration.ofMinutes(1)));

        assertThrows(IllegalStateException.class, () ->
                locks.runWhileOwned(name, "owner", Duration.ofMinutes(1), () -> {
                    new JobLock(effect, "effect", Instant.now(), Instant.now().plusSeconds(60)).persist();
                    throw new IllegalStateException("rollback da unidade");
                }));
        assertEquals(0L, QuarkusTransaction.requiringNew().call(() -> JobLock.count("jobName", effect)));
        assertTrue(locks.runWhileOwned(name, "owner", Duration.ofMinutes(1), () -> {}));
        locks.release(name, "owner");
    }

    private void expire(String name) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE job_locks SET expires_at = ? WHERE job_name = ?")) {
            statement.setObject(1, Instant.now().minusSeconds(1));
            statement.setString(2, name);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
