package br.com.satoshipet.api.outbox;

import br.com.satoshipet.api.events.DomainEventType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class OutboxServiceTest {

    @Inject
    OutboxService outboxService;

    @Test
    @Transactional
    void persisteEventoComCamposCorretos() {
        UUID id = UUID.randomUUID();

        outboxService.save(
                id,
                "Account",
                id.toString(),
                "BITCOIN_TRANSACTION_OBSERVED",
                "{\"txid\":\"abc\",\"amountSats\":100000}",
                UUID.randomUUID().toString()
        );

        OutboxEvent saved = OutboxEvent.findById(id);

        assertNotNull(saved, "Evento deve estar persistido");
        assertEquals("Account", saved.aggregateType);
        assertEquals("BITCOIN_TRANSACTION_OBSERVED", saved.eventType);
        assertNotNull(saved.createdAt);
        assertNotNull(saved.payload);
        assertEquals(0, saved.retries);
    }

    @Test
    @Transactional
    void persisteEventoComTipoDoCatalogo() {
        UUID id = UUID.randomUUID();

        outboxService.save(
                id,
                "Address",
                "address-" + id,
                DomainEventType.BITCOIN_BALANCE_RECONCILED,
                "{}",
                null
        );

        OutboxEvent saved = OutboxEvent.findById(id);

        assertNotNull(saved);
        assertEquals("BITCOIN_BALANCE_RECONCILED", saved.eventType);
    }

    @Test
    @Transactional
    void insertIdempotente_duplicacaoNaoGeraSegundoRegistro() {
        UUID id = UUID.randomUUID();
        String payload1 = "{\"petId\":\"x\",\"sats\":50000}";
        String payload2 = "{\"petId\":\"x\",\"sats\":99999}";

        outboxService.save(id, "Pet", id.toString(), "PET_FEEDING_APPLIED", payload1, null);

        // Segunda chamada com o mesmo id — deve ser ignorada (ON CONFLICT DO NOTHING)
        outboxService.save(id, "Pet", id.toString(), "PET_FEEDING_APPLIED", payload2, null);

        OutboxEvent saved = OutboxEvent.findById(id);

        assertNotNull(saved);
        // Payload deve ser o da primeira inserção (segunda foi ignorada)
        assertEquals(payload1, saved.payload);
    }

    @Test
    @Transactional
    void findPendingRetornaApenasNaoProcessados() {
        String uniquePrefix = "findPending-" + UUID.randomUUID();

        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();

        outboxService.save(idA, "T", uniquePrefix + "-A", "TEST_EVENT", "{}", null);
        outboxService.save(idB, "T", uniquePrefix + "-B", "TEST_EVENT", "{}", null);
        outboxService.save(idC, "T", uniquePrefix + "-C", "TEST_EVENT", "{}", null);

        // Simula processamento do evento idB
        OutboxEvent evtB = OutboxEvent.findById(idB);
        assertNotNull(evtB);
        evtB.processedAt = java.time.Instant.now();

        List<OutboxEvent> pending = OutboxEvent.findPending(100);

        // idA e idC devem estar na lista; idB não
        boolean hasA = pending.stream().anyMatch(e -> idA.equals(e.id));
        boolean hasC = pending.stream().anyMatch(e -> idC.equals(e.id));
        boolean hasB = pending.stream().anyMatch(e -> idB.equals(e.id));

        org.junit.jupiter.api.Assertions.assertTrue(hasA, "idA deve estar pendente");
        org.junit.jupiter.api.Assertions.assertTrue(hasC, "idC deve estar pendente");
        org.junit.jupiter.api.Assertions.assertFalse(hasB, "idB não deve estar pendente");
    }
}
