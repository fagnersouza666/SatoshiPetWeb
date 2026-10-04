package br.com.satoshipet.api.outbox;

import br.com.satoshipet.api.realtime.RealtimeEventCursorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxWebSocketConsumerTest {
    private RealtimeEventCursorService cursorService;
    private OutboxWebSocketConsumer consumer;

    @BeforeEach
    void setup() {
        cursorService = mock(RealtimeEventCursorService.class);
        consumer = new OutboxWebSocketConsumer(cursorService, new ObjectMapper());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BITCOIN_TRANSACTION_OBSERVED", "PET_STATE_CHANGED", "TEST_HEARTBEAT"})
    void aceitaSomenteFamiliasDoCanalPublico(String type) { assertTrue(consumer.supports(type)); }

    @Test
    void naoAceitaEventoPrivadoOuTipoAusente() {
        assertFalse(consumer.supports("DCA_RECOMMENDATION_GENERATED"));
        assertFalse(consumer.supports(null));
        assertFalse(consumer.supports(""));
    }

    @Test
    void projetaEnderecoDoAgregadoSemDependerDeConexaoLocal() {
        OutboxEvent event = event("Address", "bc1qaddress", "{}");
        consumer.consume(event);
        verify(cursorService).record(event, "bc1qaddress");
    }

    @Test
    void projetaEventoPetUsandoEnderecoPublicoDoPayload() {
        OutboxEvent event = event("Pet", UUID.randomUUID().toString(), "{\"address\":\"bc1qpet\"}");
        consumer.consume(event);
        verify(cursorService).record(event, "bc1qpet");
    }

    @Test
    void payloadAusenteOuIlegivelNaoVazaParaOutroCanal() {
        consumer.consume(event("Pet", "id", "{\"privateField\":\"secreto\"}"));
        consumer.consume(event("Pet", "id", "invalid-json"));
        verifyNoInteractions(cursorService);
    }

    @Test
    void falhaDePersistenciaPermiteRetryPeloPublisher() {
        OutboxEvent event = event("Address", "bc1qretry", "{}");
        when(cursorService.record(event, "bc1qretry")).thenThrow(new IllegalStateException("transitório"));
        assertThrows(IllegalStateException.class, () -> consumer.consume(event));
    }

    private OutboxEvent event(String aggregate, String id, String payload) {
        return OutboxEvent.create(aggregate, id, "PET_STATE_CHANGED", payload, Instant.now(), null);
    }
}
