package br.com.satoshipet.api.outbox;

import br.com.satoshipet.api.realtime.AccountWebSocket;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxWebSocketConsumerTest {

    @Mock
    AddressWebSocket addressWebSocket;

    @Mock
    AccountWebSocket accountWebSocket;

    RealtimeEventCursorService cursorService;

    OutboxWebSocketConsumer consumer;

    @BeforeEach
    void setUp() {
        cursorService = new RealtimeEventCursorService();
        consumer = new OutboxWebSocketConsumer(
                addressWebSocket, accountWebSocket, cursorService, new ObjectMapper());
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
    void supportsEventosPet() {
        assertTrue(consumer.supports("PET_FEEDING_APPLIED"));
        assertTrue(consumer.supports("PET_BORN"));
        assertTrue(consumer.supports("PET_STATE_CHANGED"));
    }

    @Test
    void naoSuportaOutrosEventos() {
        assertTrue(consumer.supports("PET_FEEDING_APPLIED"));
        assertFalse(consumer.supports("DCA_RECOMMENDATION_GENERATED"));
        assertFalse(consumer.supports(null));
        assertFalse(consumer.supports(""));
    }

    @Test
    void consumeEventoPetUsaCampoAddressDoPayload() {
        OutboxEvent event = criarEvento(
                "Pet",
                UUID.randomUUID().toString(),
                "PET_STATE_CHANGED",
                "{\"address\":\"bc1qfromPetPayload\",\"eventType\":\"PET_STATE_CHANGED\"}");
        doNothing().when(addressWebSocket).broadcast(anyString(), anyString(), any());

        assertEquals("bc1qfromPetPayload", consumer.resolveCanonical(event));
        consumer.consume(event);

        verify(addressWebSocket).broadcast(eq("bc1qfromPetPayload"), anyString(), any());
        verify(accountWebSocket, never()).send(anyString(), anyString(), any());
    }

    @Test
    void consumeEventoPetNaoEspalhaNoWebSocketDeConta() {
        OutboxEvent event = criarEvento(
                "Pet",
                UUID.randomUUID().toString(),
                "PET_FEEDING_APPLIED",
                "{\"address\":\"bc1qpetpublic\",\"eventType\":\"PET_FEEDING_APPLIED\",\"amountSats\":5000}");
        doNothing().when(addressWebSocket).broadcast(anyString(), anyString(), any());

        consumer.consume(event);

        verify(accountWebSocket, never()).send(anyString(), anyString(), any());
        verify(addressWebSocket).broadcast(eq("bc1qpetpublic"), anyString(), any());
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
