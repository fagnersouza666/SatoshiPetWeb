package br.com.satoshipet.api.outbox;

import br.com.satoshipet.api.job.JobLockService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class OutboxLeaseRegressionTest {
    @Test
    void naoEntregaEventoQuandoLeaseFoiPerdidaAntesDaUnidade() {
        UUID eventId = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            OutboxEvent.update("processedAt = ?1 WHERE processedAt IS NULL", Instant.now());
            OutboxEvent.createWithId(eventId, "LeaseRegression", UUID.randomUUID().toString(),
                    "LEASE_TEST", "{}", Instant.now(), null).persist();
        });
        AtomicInteger consumed = new AtomicInteger();
        JobLockService locks = new JobLockService(null, null) {
            @Override public boolean acquire(String job, String owner, Duration ttl) { return true; }
            @Override public boolean runWhileOwned(String job, String owner, Duration ttl, Runnable work) {
                return false;
            }
            @Override public void release(String job, String owner) {}
        };
        OutboxConsumer consumer = new OutboxConsumer() {
            @Override public boolean supports(String eventType) { return eventType.equals("LEASE_TEST"); }
            @Override public void consume(OutboxEvent event) { consumed.incrementAndGet(); }
        };
        OutboxPublisher publisher = new OutboxPublisher(locks, List.of(consumer));

        QuarkusTransaction.requiringNew().run(publisher::poll);

        assertEquals(0, consumed.get(), "Aquisição inicial não autoriza consumo depois da perda da lease");
        QuarkusTransaction.requiringNew().run(() ->
                assertNull(((OutboxEvent) OutboxEvent.findById(eventId)).processedAt));
    }
}
