package br.com.satoshipet.api.realtime;

import br.com.satoshipet.api.account.SessionService;
import br.com.satoshipet.api.platform.SessionAuthFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Canal WebSocket para notificações de uma conta autenticada.
 *
 * <p>Caminho: {@code /api/ws/account/{accountId}}
 *
 * <p>Usado para notificações de DCA, alertas e atualizações de saldo que
 * dependem do contexto privado da conta. O cookie {@code sp_session} do
 * handshake deve identificar a mesma conta do caminho; cookies ausentes,
 * expirados ou de outra conta são encerrados com política 1008.
 * </p>
 */
@WebSocket(path = "/api/ws/account/{accountId}")
@ApplicationScoped
public class AccountWebSocket {

    private static final Logger LOG = Logger.getLogger(AccountWebSocket.class);
    private static final int POLICY_VIOLATION = 1008;

    private final OpenConnections openConnections;
    private final ObjectMapper objectMapper;
    private final SessionService sessionService;

    @Inject
    public AccountWebSocket(
            OpenConnections openConnections,
            ObjectMapper objectMapper,
            SessionService sessionService
    ) {
        this.openConnections = openConnections;
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
    }

    /** Envia snapshot inicial somente depois de autorizar a conta do caminho. */
    @OnOpen
    @Transactional
    public String onOpen(WebSocketConnection connection) {
        Optional<UUID> requestedAccount = requestedAccountId(connection);
        if (requestedAccount.isEmpty() || !isAuthorized(connection, requestedAccount.get())) {
            reject(connection);
            return null;
        }

        String accountId = requestedAccount.get().toString();
        LOG.debugf("Nova conexão no canal privado account:%s id=%s", accountId, connection.id());

        AccountSnapshot snapshot = new AccountSnapshot(accountId);
        return serializeOrNull(new WebSocketSnapshot<>(WebSocketCursor.initial(), snapshot));
    }

    /** Processa PONG do heartbeat sem aceitar mensagens de uma sessão revogada. */
    @OnTextMessage
    @Transactional
    public String onMessage(WebSocketConnection connection, String rawMessage) {
        Optional<UUID> requestedAccount = requestedAccountId(connection);
        if (requestedAccount.isEmpty() || !isAuthorized(connection, requestedAccount.get())) {
            reject(connection);
            return null;
        }

        try {
            WebSocketClientMessage message = objectMapper.readValue(rawMessage, WebSocketClientMessage.class);
            if ("PONG".equals(message.type())) {
                LOG.debugf("PONG recebido de account=%s", requestedAccount.get());
            }
        } catch (JsonProcessingException e) {
            LOG.warnf("Mensagem inválida de %s", connection.id());
        }
        return null;
    }

    /** Loga fechamento da conexão. */
    @OnClose
    public void onClose(WebSocketConnection connection) {
        LOG.debugf("Conexão encerrada: account:%s id=%s",
                connection.pathParam("accountId"), connection.id());
    }

    /**
     * Envia evento para conexões autenticadas da conta indicada.
     *
     * @param accountId identificador UUID da conta (em string)
     * @param cursor    cursor do evento
     * @param data      payload JSON de DTO privado já autorizado pelo produtor
     */
    @Transactional
    public void send(String accountId, String cursor, JsonNode data) {
        Objects.requireNonNull(data, "data não pode ser nulo");
        UUID requestedAccount;
        try {
            requestedAccount = UUID.fromString(accountId);
        } catch (IllegalArgumentException e) {
            LOG.warnf("Ignorando fan-out para accountId inválido");
            return;
        }

        String message = serializeOrNull(new WebSocketEvent(WebSocketCursor.of(cursor), data));
        if (message == null) return;

        openConnections.stream()
                .filter(c -> c.isOpen()
                        && accountId.equals(c.pathParam("accountId"))
                        && isAuthorized(c, requestedAccount))
                .forEach(c -> {
                    try {
                        c.sendTextAndAwait(message);
                    } catch (Exception e) {
                        LOG.debugf("Falha ao enviar evento para account=%s", accountId);
                    }
                });
    }

    /** Extrai o {@code accountId} da rota sem aceitar valores arbitrários. */
    private Optional<UUID> requestedAccountId(WebSocketConnection connection) {
        try {
            return Optional.of(UUID.fromString(connection.pathParam("accountId")));
        } catch (IllegalArgumentException | NullPointerException e) {
            LOG.warnf("Rejeitando conexão privada com accountId inválido");
            return Optional.empty();
        }
    }

    /** Autoriza somente a conta identificada pelo cookie de sessão atual. */
    private boolean isAuthorized(WebSocketConnection connection, UUID requestedAccount) {
        String sessionToken = cookieValue(
                connection.handshakeRequest().header("Cookie"),
                SessionAuthFilter.SESSION_COOKIE
        );
        if (sessionToken == null) {
            return false;
        }

        return sessionService.findActive(sessionToken, Instant.now())
                .map(session -> session.account)
                .map(account -> account.id)
                .filter(requestedAccount::equals)
                .isPresent();
    }

    /** Fecha o canal sem revelar se a conta existe ou se o cookie expirou. */
    private void reject(WebSocketConnection connection) {
        LOG.warnf("Acesso rejeitado ao canal WebSocket privado connection=%s", connection.id());
        connection.closeAndAwait(new CloseReason(
                POLICY_VIOLATION, "Autenticação necessária para este canal"));
    }

    /**
     * Lê um cookie específico sem registrar ou decodificar o valor bruto do
     * token. O formato emitido pelo servidor é compatível com esta leitura.
     */
    static String cookieValue(String header, String name) {
        if (header == null || header.isBlank() || name == null || name.isBlank()) {
            return null;
        }
        for (String part : header.split(";")) {
            int separator = part.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String cookieName = part.substring(0, separator).trim();
            if (name.equals(cookieName)) {
                String value = part.substring(separator + 1).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private String serializeOrNull(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            LOG.errorf(e, "Falha ao serializar mensagem WebSocket");
            return null;
        }
    }
}
