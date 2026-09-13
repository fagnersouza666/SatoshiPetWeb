package br.com.satoshipet.api.outbox;

import br.com.satoshipet.api.job.JobLockService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
    @Transactional
    void eventoComLockDetidoPorOutraInstanciaPermanecePendente() {
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
}
