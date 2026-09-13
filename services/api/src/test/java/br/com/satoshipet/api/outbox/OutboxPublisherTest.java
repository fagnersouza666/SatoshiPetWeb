package br.com.satoshipet.api.outbox;

import br.com.satoshipet.api.job.JobLockService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    @Transactional
    void eventoComLockDetidoPorOutraInstanciaPermanecePendente() {
        markExistingPendingAsProcessed();
        String aggregateId = "account-lock-" + UUID.randomUUID();
        String lockName = OutboxPublisher.domainLockName("Account", aggregateId);
        String otherOwner = "other-owner-" + UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        assertTrue(jobLockService.acquire(lockName, otherOwner, Duration.ofMinutes(1)));
        try {
            outboxService.save(
                    eventId,
                    "Account",
                    aggregateId,
                    "UNHANDLED_EVENT",
                    "{}",
                    null
            );

            publisher.poll();

            OutboxEvent event = OutboxEvent.findById(eventId);
            assertNotNull(event);
            assertNull(event.processedAt, "Evento bloqueado deve continuar pendente");
        } finally {
            jobLockService.release(lockName, otherOwner);
        }
    }

    @Test
    @Transactional
    void eventosDeAgregadosDiferentesSaoProcessadosNoMesmoCiclo() {
        markExistingPendingAsProcessed();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        outboxService.save(
                firstId,
                "Account",
                "account-first-" + UUID.randomUUID(),
                "UNHANDLED_EVENT",
                "{}",
                null
        );
        outboxService.save(
                secondId,
                "Account",
                "account-second-" + UUID.randomUUID(),
                "UNHANDLED_EVENT",
                "{}",
                null
        );

        publisher.poll();

        OutboxEvent first = OutboxEvent.findById(firstId);
        OutboxEvent second = OutboxEvent.findById(secondId);
        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(first.processedAt);
        assertNotNull(second.processedAt);
    }

    @Test
    @Transactional
    void falhaMantemEventoPendenteEProximoPollProcessaSemReentrega() {
        markExistingPendingAsProcessed();
        UUID eventId = UUID.randomUUID();
        String aggregateId = "account-retry-" + UUID.randomUUID();
        // Prefixo fora de BITCOIN_/PET_/TEST_ para o consumidor WS de produção
        // não interceptar o evento antes do FailingOnceConsumer.
        String eventType = "RETRY_ONCE_" + UUID.randomUUID().toString().substring(0, 8);
        FailingOnceConsumer consumer = new FailingOnceConsumer(eventType);
        JobLockService locks = mock(JobLockService.class);
        when(locks.acquire(anyString(), anyString(), eq(OutboxPublisher.LOCK_TTL))).thenReturn(true);
        OutboxPublisher testPublisher = new OutboxPublisher(locks, List.of(consumer));

        outboxService.save(eventId, "Account", aggregateId, eventType, "{}", null);

        testPublisher.processNextBatch();

        OutboxEvent afterFailure = OutboxEvent.findById(eventId);
        assertNotNull(afterFailure);
        assertNull(afterFailure.processedAt, "Falha do consumidor não pode marcar evento como processado");
        assertEquals(1, afterFailure.retries);
        assertEquals(1, consumer.attempts);

        testPublisher.processNextBatch();

        OutboxEvent afterRetry = OutboxEvent.findById(eventId);
        assertNotNull(afterRetry);
        assertNotNull(afterRetry.processedAt, "Retry bem-sucedido deve marcar evento como processado");
        assertEquals(1, afterRetry.retries);
        assertEquals(2, consumer.attempts);

        testPublisher.processNextBatch();

        assertEquals(2, consumer.attempts, "Evento processado não deve ser entregue novamente");
        verify(locks, times(2)).acquire(anyString(), anyString(), eq(OutboxPublisher.LOCK_TTL));
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
