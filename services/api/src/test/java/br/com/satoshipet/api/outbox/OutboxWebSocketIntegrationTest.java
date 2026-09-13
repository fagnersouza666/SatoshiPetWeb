package br.com.satoshipet.api.outbox;

import br.com.satoshipet.api.realtime.RealtimeEventCursorService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Teste de integração (IT) que verifica o caminho consumidor do outbox
 * via invocação direta do {@link OutboxWebSocketConsumer}.
 *
 * <p>Usa {@code @QuarkusTest} (H2) para validar que o consumer é corretamente
 * descoberto e que o {@link RealtimeEventCursorService} avança o cursor ao consumir
 * um evento — sem precisar de WebSocket real.</p>
 */
@QuarkusTest
class OutboxWebSocketIntegrationTest {

    @Inject
    OutboxWebSocketConsumer consumer;

    @Inject
    RealtimeEventCursorService cursorService;

    @Inject
    OutboxService outboxService;

    @Test
    @Transactional
    void consumerEhDescobertoPeloCDI() {
        // Verifica que o consumer foi instanciado via CDI corretamente
        org.junit.jupiter.api.Assertions.assertNotNull(consumer);
        org.junit.jupiter.api.Assertions.assertTrue(consumer.supports("BITCOIN_TRANSACTION_OBSERVED"));
        org.junit.jupiter.api.Assertions.assertTrue(consumer.supports("PET_STATE_CHANGED"));
    }

    @Test
    @Transactional
    void consumerAvancaCursorAoProcessarEventoDeAddress() {
        String canonical = "bc1q-integration-test-" + UUID.randomUUID().toString().substring(0, 8);
        String cursorAntes = cursorService.currentCursor(canonical);
        assertEquals("0", cursorAntes);

        OutboxEvent event = OutboxEvent.create(
                "Address",
                canonical,
                "BITCOIN_TRANSACTION_OBSERVED",
                "{\"txid\":\"abc123\",\"amountSats\":50000}",
                Instant.now(),
                null
        );

        // Chama consume diretamente sem precisar de WebSocket real
        // (a ausência de conexões WebSocket abertas faz broadcast ser no-op)
        assertDoesNotThrow(() -> consumer.consume(event));

        String cursorDepois = cursorService.currentCursor(canonical);
        org.junit.jupiter.api.Assertions.assertNotEquals("0", cursorDepois,
                "Cursor deve ter avançado após o consumo do evento");
    }

    @Test
    @Transactional
    void consumerAvancaCursorAoProcessarEventoPet() {
        String canonical = "bc1q-pet-it-" + UUID.randomUUID().toString().substring(0, 8);
        assertEquals("0", cursorService.currentCursor(canonical));

        OutboxEvent event = OutboxEvent.create(
                "Pet",
                UUID.randomUUID().toString(),
                "PET_STATE_CHANGED",
                "{\"address\":\"" + canonical + "\",\"eventType\":\"PET_STATE_CHANGED\","
                        + "\"occurredAt\":\"2026-09-13T15:00:00Z\",\"presentation\":\"CREATURE\","
                        + "\"emotionalState\":\"ALIMENTADO\",\"reserveHours\":\"6.0000000000\","
                        + "\"awaitingReference\":false}",
                Instant.now(),
                null
        );

        assertDoesNotThrow(() -> consumer.consume(event));
        org.junit.jupiter.api.Assertions.assertNotEquals("0", cursorService.currentCursor(canonical));
    }

    @Test
    @Transactional
    void consumerSuportaEventoTestEventType() {
        String canonical = "bc1q-test-type-" + UUID.randomUUID().toString().substring(0, 8);

        OutboxEvent event = OutboxEvent.create(
                "Address",
                canonical,
                "TEST_HEARTBEAT",
                "{\"status\":\"ok\"}",
                Instant.now(),
                null
        );

        assertDoesNotThrow(() -> consumer.consume(event));
    }

    @Test
    @Transactional
    void outboxServicePersistAndConsumerProcessa() {
        UUID eventId = UUID.randomUUID();
        String canonical = "bc1q-outbox-it-" + UUID.randomUUID().toString().substring(0, 8);

        outboxService.save(
                eventId,
                "Address",
                canonical,
                "BITCOIN_TRANSACTION_OBSERVED",
                "{\"txid\":\"itTest\",\"amountSats\":1000}",
                "corr-it-test"
        );

        OutboxEvent saved = OutboxEvent.findById(eventId);
        org.junit.jupiter.api.Assertions.assertNotNull(saved, "Evento deve estar salvo");

        // Invoca consumer diretamente
        assertDoesNotThrow(() -> consumer.consume(saved));

        // Cursor deve ter avançado
        String cursor = cursorService.currentCursor(canonical);
        org.junit.jupiter.api.Assertions.assertNotEquals("0", cursor);
    }
}
