package br.com.satoshipet.api.realtime;

import br.com.satoshipet.api.platform.CorrelationIdContext;
import br.com.satoshipet.api.support.LogCapture;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.HandshakeRequest;
import io.quarkus.websockets.next.UserData;
import io.quarkus.websockets.next.WebSocketConnection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AddressWebSocketLoggingTest {
    @Test
    void mensagemInvalidaNaoRegistraPayloadEConservaCorrelacao() {
        AddressWebSocket socket = new AddressWebSocket();
        socket.objectMapper = new ObjectMapper();
        WebSocketConnection connection = mock(WebSocketConnection.class);
        HandshakeRequest handshake = mock(HandshakeRequest.class);
        when(connection.userData()).thenReturn(mock(UserData.class));
        when(connection.handshakeRequest()).thenReturn(handshake);
        when(handshake.header(CorrelationIdContext.HEADER)).thenReturn("ws-request-log");
        when(connection.id()).thenReturn("connection-123");
        String privateContents = "conteudo-privado-nao-registrar";

        try (LogCapture logs = new LogCapture(AddressWebSocket.class)) {
            try (CorrelationIdContext.Scope ignored = CorrelationIdContext.open("outer-operation")) {
                assertNull(socket.onMessage(connection, "{" + privateContents));
                assertEquals("outer-operation", CorrelationIdContext.current());
            }
            assertNull(CorrelationIdContext.current());
            assertEquals(1, logs.entries().size());
            LogCapture.Entry warning = logs.entries().getFirst();
            assertEquals("ws-request-log", warning.correlationId());
            assertFalse(warning.message().contains(privateContents));
            assertEquals("Mensagem inválida de connection-123", warning.message());
        }
    }
}
