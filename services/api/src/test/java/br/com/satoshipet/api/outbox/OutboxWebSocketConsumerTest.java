package br.com.satoshipet.api.outbox;

import br.com.satoshipet.api.realtime.AddressWebSocket;
import br.com.satoshipet.api.realtime.RealtimeEventCursorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxWebSocketConsumerTest {

    @Mock
    AddressWebSocket addressWebSocket;

    RealtimeEventCursorService cursorService;

    OutboxWebSocketConsumer consumer;

    @BeforeEach
    void setUp() {
        cursorService = new RealtimeEventCursorService();
        consumer = new OutboxWebSocketConsumer(addressWebSocket, cursorService, new ObjectMapper());
    }

    @Test
    void supportsEventosBitcoin() {
        assertTrue(consumer.supports("BITCOIN_TRANSACTION_OBSERVED"));
        assertTrue(consumer.supports("BITCOIN_FEEDING_APPLIED"));
    }

    @Test
    void supportsEventosTeste() {
        assertTrue(consumer.supports("TEST_EVENT"));
        assertTrue(consumer.supports("TEST_HEARTBEAT"));
    }

    @Test
    void naoSuportaOutrosEventos() {
        assertFalse(consumer.supports("PET_FEEDING_APPLIED"));
        assertFalse(consumer.supports("DCA_RECOMMENDATION_GENERATED"));
        assertFalse(consumer.supports(null));
        assertFalse(consumer.supports(""));
    }

    @Test
    void consumeEventoDeAddressETransmiteViaBroadcast() {
        OutboxEvent event = criarEvento("Address", "bc1qtest1234", "BITCOIN_TRANSACTION_OBSERVED",
                "{\"txid\":\"abc\",\"amountSats\":100000}");
        doNothing().when(addressWebSocket).broadcast(anyString(), anyString(), any());

        consumer.consume(event);

        verify(addressWebSocket).broadcast(eq("bc1qtest1234"), anyString(), any());
    }

    @Test
    void reentregaDoMesmoEventoNaoCriaNovoCursorNemBroadcast() {
        OutboxEvent event = criarEvento("Address", "bc1qdedup", "BITCOIN_TRANSACTION_OBSERVED",
                "{\"txid\":\"abc\"}");
        doNothing().when(addressWebSocket).broadcast(anyString(), anyString(), any());

        consumer.consume(event);
        String cursorAplicado = cursorService.currentCursor("bc1qdedup");
        consumer.consume(event);

        assertEquals(cursorAplicado, cursorService.currentCursor("bc1qdedup"));
        verify(addressWebSocket, times(1)).broadcast(eq("bc1qdedup"), anyString(), any());
    }

    @Test
    void falhaNaAplicacaoDeixaEventoDisponivelParaRetry() {
        OutboxEvent event = criarEvento("Address", "bc1qretry", "BITCOIN_TRANSACTION_OBSERVED",
                "{\"txid\":\"retry\"}");
        doThrow(new IllegalStateException("falha transitória"))
                .doNothing()
                .when(addressWebSocket)
                .broadcast(anyString(), anyString(), any());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> consumer.consume(event));
        consumer.consume(event);

        verify(addressWebSocket, times(2)).broadcast(eq("bc1qretry"), anyString(), any());
    }

    @Test
    void consumeEventoNaoAddressUsaCampoAddressDoPayload() {
        OutboxEvent event = criarEvento("Account", "some-account-id", "BITCOIN_TRANSACTION_OBSERVED",
                "{\"address\":\"bc1qfromPayload\",\"txid\":\"xyz\"}");
        doNothing().when(addressWebSocket).broadcast(anyString(), anyString(), any());

        consumer.consume(event);

        verify(addressWebSocket).broadcast(eq("bc1qfromPayload"), anyString(), any());
    }

    @Test
    void consumeEventoSemEnderecoNaoChama() {
        OutboxEvent event = criarEvento("Account", "acc-id", "BITCOIN_TRANSACTION_OBSERVED",
                "{\"txid\":\"xyz\",\"noAddress\":true}");

        consumer.consume(event);

        verify(addressWebSocket, never()).broadcast(anyString(), anyString(), any());
    }

    @Test
    void resolveCanonicalParaAggregateTypeAddress() {
        OutboxEvent event = criarEvento("Address", "bc1qtarget", "BITCOIN_TRANSACTION_OBSERVED", "{}");

        String canonical = consumer.resolveCanonical(event);

        assertNotNull(canonical);
        assertTrue(canonical.equals("bc1qtarget"));
    }

    @Test
    void resolveCanonicalRetornaNullSemCampoAddress() {
        OutboxEvent event = criarEvento("Other", "id123", "BITCOIN_TRANSACTION_OBSERVED", "{\"foo\":\"bar\"}");

        String canonical = consumer.resolveCanonical(event);

        assertNull(canonical);
    }

    private OutboxEvent criarEvento(String aggregateType, String aggregateId, String eventType, String payload) {
        return OutboxEvent.create(aggregateType, aggregateId, eventType, payload, Instant.now(), null);
    }
}
