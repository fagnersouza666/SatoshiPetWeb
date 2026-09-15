package br.com.satoshipet.api.outbox;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(UUID.fromString(saved.correlationId).toString(), saved.correlationId);
        assertEquals(0, saved.retries);
    }

    @Test
    @Transactional
    void geraCorrelationIdQuandoChamadorNaoFornece() {
        UUID id = UUID.randomUUID();

        outboxService.save(id, "Account", id.toString(), "TEST_EVENT", "{}", null);

        OutboxEvent saved = OutboxEvent.findById(id);
        assertNotNull(saved);
        assertNotNull(saved.correlationId);
        assertEquals(36, saved.correlationId.length());
        assertNotNull(UUID.fromString(saved.correlationId));
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

        assertTrue(hasA, "idA deve estar pendente");
        assertTrue(hasC, "idC deve estar pendente");
        assertFalse(hasB, "idB não deve estar pendente");
    }

    @Test
    @Transactional
    void findPendingOrdenaPorIdQuandoCreatedAtEmpata() {
        Instant createdAt = Instant.parse("2000-01-01T00:00:00Z");
        UUID lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higherId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        OutboxEvent higher = OutboxEvent.createWithId(
                higherId, "Test", higherId.toString(), "TEST_EVENT", "{}", createdAt, null);
        OutboxEvent lower = OutboxEvent.createWithId(
                lowerId, "Test", lowerId.toString(), "TEST_EVENT", "{}", createdAt, null);
        higher.persist();
        lower.persist();

        List<OutboxEvent> pending = OutboxEvent.findPending(100);
        int lowerIndex = pending.indexOf(lower);
        int higherIndex = pending.indexOf(higher);

        assertTrue(lowerIndex >= 0, "Evento com menor id deve estar pendente");
        assertTrue(higherIndex >= 0, "Evento com maior id deve estar pendente");
        assertTrue(lowerIndex < higherIndex,
                "Eventos com o mesmo createdAt devem ser ordenados por id ASC");
    }

    @Test
    @Transactional
    void findPendingLimitaQuantidadeRetornada() {
        outboxService.save(UUID.randomUUID(), "Test", UUID.randomUUID().toString(),
                "TEST_EVENT", "{}", null);
        outboxService.save(UUID.randomUUID(), "Test", UUID.randomUUID().toString(),
                "TEST_EVENT", "{}", null);

        List<OutboxEvent> pending = OutboxEvent.findPending(2);

        assertEquals(2, pending.size(), "Consulta deve respeitar o tamanho do lote");
    }

    @Test
    void findPendingRejeitaLimiteNaoPositivo() {
        assertThrows(IllegalArgumentException.class, () -> OutboxEvent.findPending(0));
        assertThrows(IllegalArgumentException.class, () -> OutboxEvent.findPending(-1));
    }
}
