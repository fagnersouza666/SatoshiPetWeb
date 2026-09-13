package br.com.satoshipet.api.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WebSocketMessageDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void snapshotSerializaEstadoExplicitoESeuCursor() throws Exception {
        WebSocketSnapshot<AddressSnapshot> snapshot = new WebSocketSnapshot<>(
                WebSocketCursor.of("42"),
                AddressSnapshot.initial("bc1qpublico", "HIBERNANDO"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(snapshot));

        assertEquals("SNAPSHOT", json.get("type").asText());
        assertEquals("42", json.get("cursor").asText());
        assertEquals("bc1qpublico", json.get("data").get("address").asText());
        assertEquals("HIBERNANDO", json.get("data").get("state").asText());
        assertFalse(json.get("data").has("resumedFrom"));
    }

    @Test
    void cursorContinuaStringAoSerializarEReconheceMensagemDoCliente() throws Exception {
        WebSocketClientMessage reconnect = objectMapper.readValue(
                "{\"type\":\"RECONNECT\",\"cursor\":\"123\"}",
                WebSocketClientMessage.class);

        assertEquals("RECONNECT", reconnect.type());
        assertEquals("123", reconnect.cursor().value());
        assertEquals("123", objectMapper.readTree(objectMapper.writeValueAsString(
                new WebSocketSnapshot<>(reconnect.cursor(),
                        AddressSnapshot.initial("bc1qpublico", "HIBERNANDO")))).get("cursor").asText());
    }

    @Test
    void eventoSerializaPayloadRedigidoSemMapaDeEnvelope() throws Exception {
        WebSocketEvent event = WebSocketEvent.from(
                "7", Map.of("eventType", "BITCOIN_TRANSACTION_OBSERVED", "txid", "tx-1"),
                objectMapper);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(event));

        assertEquals("EVENT", json.get("type").asText());
        assertEquals("7", json.get("cursor").asText());
        assertEquals("BITCOIN_TRANSACTION_OBSERVED", json.get("data").get("eventType").asText());
        assertEquals("tx-1", json.get("data").get("txid").asText());
    }

    @Test
    void pingNaoExpoeCursorNemPayload() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(new WebSocketPing()));

        assertEquals("PING", json.get("type").asText());
        assertEquals(1, json.size());
    }
}
