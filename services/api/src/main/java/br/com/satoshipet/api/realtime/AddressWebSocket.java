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

import java.util.ArrayList;
import java.util.List;

/**
 * Canal WebSocket para acompanhamento de um endereço Bitcoin em tempo real.
 *
 * <p>Caminho: {@code /api/ws/address/{canonical}}
 *
 * <p>Protocolo:
 * <ol>
 *   <li>Ao conectar, o servidor envia um {@code SNAPSHOT} com o estado atual
 *       e o cursor da última posição conhecida.</li>
 *   <li>Eventos incrementais são enviados como {@code EVENT} com cursor
 *       atualizado.</li>
 *   <li>O servidor envia {@code PING} a cada 30 s; o cliente deve responder
 *       com {@code PONG}.</li>
 *   <li>Para reconexão, o cliente envia {@code {"type":"RECONNECT","cursor":"N"}}
 *       e recebe apenas os eventos posteriores ao cursor (replay do ring buffer).</li>
 * </ol>
 * </p>
 */
@WebSocket(path = "/api/ws/address/{canonical}")
@ApplicationScoped
public class AddressWebSocket {

    private static final Logger LOG = Logger.getLogger(AddressWebSocket.class);

    @Inject
    OpenConnections openConnections;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    RealtimeEventCursorService cursorService;

    /**
     * Envia o snapshot inicial ao conectar, usando o cursor atual do endereço.
     * Se não houver eventos anteriores, cursor será "0".
     */
    @OnOpen
    public String onOpen(WebSocketConnection connection) {
        String canonical = connection.pathParam("canonical");
        LOG.debugf("Nova conexão no canal address:%s id=%s", canonical, connection.id());

        String cursor = cursorService.currentCursor(canonical);

        // Snapshot mínimo — estado HIBERNANDO enquanto PetLifecycle não está implementado.
        AddressSnapshot stateData = AddressSnapshot.initial(canonical, "HIBERNANDO");

        return serializeOrNull(new WebSocketSnapshot<>(WebSocketCursor.of(cursor), stateData));
    }

    /**
     * Processa mensagens do cliente (PONG e RECONNECT).
     * Para RECONNECT, reproduz eventos posteriores ao cursor informado.
     */
    @OnTextMessage
    public String onMessage(WebSocketConnection connection, String rawMessage) {
        try {
            WebSocketClientMessage msg = objectMapper.readValue(rawMessage, WebSocketClientMessage.class);

            return switch (msg.type()) {
                case "PONG" -> {
                    LOG.debugf("PONG recebido de %s", connection.id());
                    yield null; // sem resposta ao PONG
                }
                case "RECONNECT" -> {
                    String requestedCursor = msg.cursor() != null ? msg.cursor().value() : "0";
                    LOG.debugf("Reconexão com cursor=%s para %s", requestedCursor, connection.id());
                    String canonical = connection.pathParam("canonical");

                    // Replay dos eventos posteriores ao cursor informado
                    List<RealtimeEventCursorService.StoredEvent> missed =
                            cursorService.eventsAfter(canonical, requestedCursor);

                    if (missed.isEmpty()) {
                        // Nenhum evento perdido — confirma o cursor solicitado pelo cliente
                        AddressSnapshot stateData = AddressSnapshot.resumed(
                                canonical, "HIBERNANDO", requestedCursor);
                        yield serializeOrNull(new WebSocketSnapshot<>(
                                WebSocketCursor.of(requestedCursor), stateData));
                    }

                    // Envia cada evento perdido como EVENT; retorna apenas o primeiro via yield
                    // (os demais são enviados assincronamente)
                    List<String> serialized = new ArrayList<>();
                    for (RealtimeEventCursorService.StoredEvent event : missed) {
                        String s = serializeOrNull(WebSocketEvent.from(
                                event.cursor(), event.data(), objectMapper));
                        if (s != null) {
                            serialized.add(s);
                        }
                    }

                    if (serialized.isEmpty()) {
                        yield null;
                    }
                    // Envia eventos adicionais diretamente na conexão
                    for (int i = 1; i < serialized.size(); i++) {
                        String payload = serialized.get(i);
                        try {
                            connection.sendTextAndAwait(payload);
                        } catch (Exception e) {
                            LOG.debugf("Falha ao enviar evento de replay para %s", connection.id());
                        }
                    }
                    yield serialized.get(0);
                }
                default -> {
                    LOG.warnf("Mensagem desconhecida type=%s de %s", msg.type(), connection.id());
                    yield null;
                }
            };

        } catch (JsonProcessingException e) {
            LOG.warnf("Mensagem inválida de %s: %s", connection.id(), rawMessage);
            return null;
        }
    }

    /** Loga o fechamento da conexão. */
    @OnClose
    public void onClose(WebSocketConnection connection) {
        LOG.debugf("Conexão encerrada: address:%s id=%s",
                connection.pathParam("canonical"), connection.id());
    }

    /**
     * Transmite um evento para todos os clientes conectados ao canal do endereço
     * informado. Chamado por {@link OutboxWebSocketConsumer} quando um evento
     * é publicado.
     *
     * @param canonical forma canônica do endereço Bitcoin
     * @param cursor    novo cursor após o evento
     * @param data      dados do evento a transmitir
     */
    public void broadcast(String canonical, String cursor, Object data) {
        String message = serializeOrNull(WebSocketEvent.from(cursor, data, objectMapper));
        if (message == null) return;

        openConnections.stream()
                .filter(c -> c.isOpen() && canonical.equals(c.pathParam("canonical")))
                .forEach(c -> {
                    try {
                        c.sendTextAndAwait(message);
                    } catch (Exception e) {
                        LOG.debugf("Falha ao enviar evento para %s", c.id());
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
