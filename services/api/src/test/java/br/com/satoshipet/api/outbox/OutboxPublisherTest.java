package br.com.satoshipet.api.outbox;

import br.com.satoshipet.api.job.JobLockService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OutboxPublisherTest {

    @Inject
    OutboxPublisher publisher;

    @Inject
    OutboxService outboxService;

    @Inject
    JobLockService jobLockService;

    @Test
    void mesmaChaveDeAgregadoGeraMesmoLock() {
        assertEquals(
                OutboxPublisher.domainLockName("Address", "bc1qsame"),
                OutboxPublisher.domainLockName("Address", "bc1qsame")
        );
        assertNotEquals(
                OutboxPublisher.domainLockName("Address", "bc1qsame"),
                OutboxPublisher.domainLockName("Address", "bc1qother")
        );
        assertTrue(OutboxPublisher.domainLockName("Address", "bc1qsame")
                .startsWith(OutboxPublisher.DOMAIN_LOCK_PREFIX));
    }

    @Test
    void nomeLongoDeAgregadoPermaneceEstavelEDentroDoLimiteDoBanco() {
        String aggregateType = "A".repeat(100);
        String aggregateId = "B".repeat(100);

        String lockName = OutboxPublisher.domainLockName(aggregateType, aggregateId);

        assertEquals(lockName, OutboxPublisher.domainLockName(aggregateType, aggregateId));
        assertTrue(lockName.length() <= 100);
        assertTrue(lockName.startsWith(OutboxPublisher.DOMAIN_LOCK_PREFIX + "sha256:"));
    }

    @Test
    void eventoComLockDetidoPorOutraInstanciaPermanecePendente() {
        QuarkusTransaction.requiringNew().run(OutboxPublisherTest::markExistingPendingAsProcessed);
        String aggregateId = "account-lock-" + UUID.randomUUID();
        String lockName = OutboxPublisher.domainLockName("Account", aggregateId);
        String otherOwner = "other-owner-" + UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        assertTrue(jobLockService.acquire(lockName, otherOwner, Duration.ofMinutes(1)));
        try {
            save(
                    eventId,
                    "Account",
                    aggregateId,
                    "UNHANDLED_EVENT",
                    "{}",
                    null
            );

            publisher.poll();

            OutboxEvent event = reload(eventId);
            assertNotNull(event);
            assertNull(event.processedAt, "Evento bloqueado deve continuar pendente");
        } finally {
            jobLockService.release(lockName, otherOwner);
        }
    }

    @Test
    void eventosDeAgregadosDiferentesSaoProcessadosNoMesmoCiclo() {
        QuarkusTransaction.requiringNew().run(OutboxPublisherTest::markExistingPendingAsProcessed);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        save(
                firstId,
                "Account",
                "account-first-" + UUID.randomUUID(),
                "UNHANDLED_EVENT",
                "{}",
                null
        );
        save(
                secondId,
                "Account",
                "account-second-" + UUID.randomUUID(),
                "UNHANDLED_EVENT",
                "{}",
                null
        );

        publisher.poll();

        OutboxEvent first = reload(firstId);
        OutboxEvent second = reload(secondId);
        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(first.processedAt);
        assertNotNull(second.processedAt);
    }

    @Test
    void falhaMantemEventoPendenteEProximoPollProcessaSemReentrega() {
        QuarkusTransaction.requiringNew().run(OutboxPublisherTest::markExistingPendingAsProcessed);
        UUID eventId = UUID.randomUUID();
        String aggregateId = "account-retry-" + UUID.randomUUID();
        // Prefixo fora de BITCOIN_/PET_/TEST_ para o consumidor WS de produção
        // não interceptar o evento antes do FailingOnceConsumer.
        String eventType = "RETRY_ONCE_" + UUID.randomUUID().toString().substring(0, 8);
        FailingOnceConsumer consumer = new FailingOnceConsumer(eventType);
        OutboxPublisher testPublisher = new OutboxPublisher(jobLockService, List.of(consumer));

        save(eventId, "Account", aggregateId, eventType, "{}", null);

        testPublisher.processNextBatch();

        OutboxEvent afterFailure = reload(eventId);
        assertNotNull(afterFailure);
        assertNull(afterFailure.processedAt, "Falha do consumidor não pode marcar evento como processado");
        assertEquals(1, afterFailure.retries);
        assertEquals(1, consumer.attempts);

        testPublisher.processNextBatch();

        OutboxEvent afterRetry = reload(eventId);
        assertNotNull(afterRetry);
        assertNotNull(afterRetry.processedAt, "Retry bem-sucedido deve marcar evento como processado");
        assertEquals(1, afterRetry.retries);
        assertEquals(2, consumer.attempts);

        testPublisher.processNextBatch();

        assertEquals(2, consumer.attempts, "Evento processado não deve ser entregue novamente");

    }

    private void save(UUID id, String aggregateType, String aggregateId, String eventType,
                      String payload, String correlationId) {
        QuarkusTransaction.requiringNew().run(() ->
                outboxService.save(id, aggregateType, aggregateId, eventType, payload, correlationId));
    }

    private static OutboxEvent reload(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> OutboxEvent.findById(id));
    }

    /** Isola o lote do publisher de eventos PET_/BITCOIN_ deixados por outros testes. */
    private static void markExistingPendingAsProcessed() {
        List<OutboxEvent> pending;
        do {
            pending = OutboxEvent.findPending(OutboxPublisher.BATCH_SIZE);
            Instant now = Instant.now();
            for (OutboxEvent event : pending) {
                event.processedAt = now;
            }
        } while (!pending.isEmpty());
    }

    private static final class FailingOnceConsumer implements OutboxConsumer {

        private final String eventType;
        private int attempts;
        private boolean failed;

        private FailingOnceConsumer(String eventType) {
            this.eventType = eventType;
        }

        @Override
        public void consume(OutboxEvent event) {
            attempts++;
            if (!failed) {
                failed = true;
                throw new IllegalStateException("falha transitória de teste");
            }
        }

        @Override
        public boolean supports(String eventType) {
            return this.eventType.equals(eventType);
        }
    }
}
