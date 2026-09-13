package br.com.satoshipet.api.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;


/**
 * Canal WebSocket para notificações de uma conta autenticada.
 *
 * <p>Caminho: {@code /api/ws/account/{accountId}}
 *
 * <p>Usado para notificações de DCA, alertas e atualizações de saldo que
 * dependem do contexto privado da conta. O accountId deve ser validado contra
 * o JWT da conexão (implementação futura de autenticação WS).
 * </p>
 */
@WebSocket(path = "/api/ws/account/{accountId}")
@ApplicationScoped
public class AccountWebSocket {

    private static final Logger LOG = Logger.getLogger(AccountWebSocket.class);

    @Inject
    OpenConnections openConnections;

    @Inject
    ObjectMapper objectMapper;

    /** Envia snapshot inicial com estado mínimo da conta. */
    @OnOpen
    public String onOpen(WebSocketConnection connection) {
        String accountId = connection.pathParam("accountId");
        LOG.debugf("Nova conexão no canal account:%s id=%s", accountId, connection.id());

        AccountSnapshot snapshot = new AccountSnapshot(accountId);
        return serializeOrNull(new WebSocketSnapshot<>(WebSocketCursor.initial(), snapshot));
    }

    /** Processa PONG do heartbeat. */
    @OnTextMessage
    public String onMessage(WebSocketConnection connection, String rawMessage) {
        try {
            WebSocketClientMessage msg = objectMapper.readValue(rawMessage, WebSocketClientMessage.class);
            if ("PONG".equals(msg.type())) {
                LOG.debugf("PONG recebido de account=%s", connection.pathParam("accountId"));
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
     * Envia evento para todos os canais abertos de uma conta.
     *
     * @param accountId identificador UUID da conta (em string)
     * @param cursor    cursor do evento
     * @param data      dados a transmitir
     */
    public void send(String accountId, String cursor, Object data) {
        String message = serializeOrNull(WebSocketEvent.from(cursor, data, objectMapper));
        if (message == null) return;

        openConnections.stream()
                .filter(c -> c.isOpen() && accountId.equals(c.pathParam("accountId")))
                .forEach(c -> {
                    try {
                        c.sendTextAndAwait(message);
                    } catch (Exception e) {
                        LOG.debugf("Falha ao enviar evento para account=%s", accountId);
                    }
                });
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
