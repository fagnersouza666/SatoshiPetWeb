package br.com.satoshipet.api.realtime;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.Session;
import br.com.satoshipet.api.account.SessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.HandshakeRequest;
import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountWebSocketTest {

    private static final String SESSION_TOKEN = "session-token";

    @Mock
    OpenConnections openConnections;

    @Mock
    SessionService sessionService;

    @Mock
    WebSocketConnection connection;

    @Mock
    HandshakeRequest handshakeRequest;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AccountWebSocket webSocket;
    private Account account;
    private Session session;

    @BeforeEach
    void setUp() {
        webSocket = new AccountWebSocket(openConnections, objectMapper, sessionService);
        Instant now = Instant.now();
        account = Account.create(
                "account-ws-" + UUID.randomUUID() + "@example.com",
                "UTC",
                "pt-BR",
                now
        );
        session = Session.create(
                account,
                "session-hash",
                "csrf-hash",
                now,
                now.plusSeconds(3600),
                "test",
                "127.0.0.1"
        );
    }

    @Test
    void snapshotAceitaSessaoDaPropriaConta() throws Exception {
        configureAuthenticatedConnection();

        String snapshot = webSocket.onOpen(connection);

        JsonNode json = objectMapper.readTree(snapshot);
        assertEquals("SNAPSHOT", json.get("type").asText());
        assertEquals("0", json.get("cursor").asText());
        assertEquals(account.id.toString(), json.get("data").get("accountId").asText());
        verify(connection, never()).close(any());
    }

    @Test
    void rejeitaConexaoSemCookieDeSessaoAntesDoSnapshot() {
        when(connection.pathParam("accountId")).thenReturn(account.id.toString());
        when(connection.handshakeRequest()).thenReturn(handshakeRequest);
        when(handshakeRequest.header("Cookie")).thenReturn(null);

        assertNull(webSocket.onOpen(connection));

        verify(connection).closeAndAwait(argThat(this::isPolicyViolation));
    }

    @Test
    void rejeitaTentativaDeAcessarOutraConta() {
        configureAuthenticatedConnection();
        UUID outraConta = UUID.randomUUID();
        when(connection.pathParam("accountId")).thenReturn(outraConta.toString());

        assertNull(webSocket.onOpen(connection));

        verify(connection).closeAndAwait(argThat(this::isPolicyViolation));
    }

    @Test
    void fanOutPrivadoIgnoraConexaoSemSessaoMesmoComMesmaRota() {
        configureAuthenticatedConnection();
        WebSocketConnection autorizado = connection;
        WebSocketConnection anonimo = org.mockito.Mockito.mock(WebSocketConnection.class);
        HandshakeRequest handshakeAnonimo = org.mockito.Mockito.mock(HandshakeRequest.class);

        when(autorizado.isOpen()).thenReturn(true);
        when(anonimo.isOpen()).thenReturn(true);
        when(anonimo.pathParam("accountId")).thenReturn(account.id.toString());
        when(anonimo.handshakeRequest()).thenReturn(handshakeAnonimo);
        when(handshakeAnonimo.header("Cookie")).thenReturn(null);
        when(openConnections.stream()).thenReturn(List.of(autorizado, anonimo).stream());

        webSocket.send(account.id.toString(), "7", objectMapper.createObjectNode().put("kind", "private"));

        verify(autorizado).sendTextAndAwait(anyString());
        verify(anonimo, never()).sendTextAndAwait(anyString());
    }

    @Test
    void cookieValueNaoConfundePrefixosDeNome() {
        assertEquals("valor", AccountWebSocket.cookieValue(
                "sp_session_extra=errado; sp_session=valor", "sp_session"));
        assertNull(AccountWebSocket.cookieValue("sp_session=", "sp_session"));
        assertNull(AccountWebSocket.cookieValue("outra=valor", "sp_session"));
        assertEquals("valor", AccountWebSocket.cookieValue("sp_session=valor", "sp_session"));
    }

    private void configureAuthenticatedConnection() {
        when(connection.handshakeRequest()).thenReturn(handshakeRequest);
        when(handshakeRequest.header("Cookie")).thenReturn("other=ok; sp_session=" + SESSION_TOKEN);
        when(sessionService.findActive(eq(SESSION_TOKEN), any(Instant.class)))
                .thenReturn(Optional.of(session));
        when(connection.pathParam("accountId")).thenReturn(account.id.toString());
    }

    private boolean isPolicyViolation(CloseReason reason) {
        return reason != null && reason.getCode() == 1008;
    }
}
