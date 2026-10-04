package br.com.satoshipet.api.realtime;

import br.com.satoshipet.api.platform.CorrelationIdContext;
import br.com.satoshipet.api.pet.PetPublicSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.UserData;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/** Canal público com snapshot e replay da projeção confirmada no banco. */
@WebSocket(path = "/api/ws/address/{canonical}")
@ApplicationScoped
public class AddressWebSocket {
    private static final Logger LOG = Logger.getLogger(AddressWebSocket.class);
    private static final UserData.TypedKey<ConnectionState> STATE =
            new UserData.TypedKey<>("satoshi-pet.address-cursor");

    @Inject OpenConnections openConnections;
    @Inject ObjectMapper objectMapper;
    @Inject RealtimeEventCursorService cursorService;

    /** Registra a conexão para fan-out somente depois de entregar o snapshot inicial. */
    @OnOpen
    @Transactional
    public void onOpen(WebSocketConnection connection) {
        try (CorrelationIdContext.Scope ignored = CorrelationIdContext.open(connection)) {
            String canonical = connection.pathParam("canonical");
            String cursor = cursorService.currentCursor(canonical);
            ConnectionState state = new ConnectionState(cursor);
            if (sendSnapshot(connection, state, cursor, null) && connection.isOpen()) {
                connection.userData().put(STATE, state);
                if (!connection.isOpen()) connection.userData().remove(STATE);
            }
        }
    }

    /** Envia todos os frames em ordem; nunca devolve um evento adicional ao framework. */
    @OnTextMessage
    @Transactional
    public String onMessage(WebSocketConnection connection, String rawMessage) {
        try (CorrelationIdContext.Scope ignored = CorrelationIdContext.open(connection)) {
            WebSocketClientMessage message;
            try {
                message = objectMapper.readValue(rawMessage, WebSocketClientMessage.class);
            } catch (JsonProcessingException exception) {
                LOG.warnf("Mensagem inválida de %s", connection.id());
                return null;
            }
            if (message == null || message.type() == null) return null;
            if ("PONG".equals(message.type())) return null;
            if (!"RECONNECT".equals(message.type())) {
                LOG.warnf("Mensagem desconhecida de %s", connection.id());
                return null;
            }
            ConnectionState state = connection.userData().get(STATE);
            if (state == null) return null;
            String requested = message.cursor() == null ? "0" : message.cursor().value();
            synchronized (state) {
                RealtimeEventCursorService.Replay replay = cursorService.replayAfter(
                        connection.pathParam("canonical"), requested);
                if (replay.snapshotRequired() || replay.events().isEmpty()) {
                    sendSnapshot(connection, state, replay.currentCursor(), requested);
                } else {
                    sendEvents(connection, state, replay);
                }
            }
            return null;
        }
    }

    /** Consulta limitada por conexão; todas as réplicas leem a mesma projeção confirmada. */
    @Transactional
    public void pollCommittedEvents() {
        openConnections.stream().filter(WebSocketConnection::isOpen).forEach(connection -> {
            ConnectionState state = connection.userData().get(STATE);
            if (state == null) return;
            synchronized (state) {
                try (CorrelationIdContext.Scope ignored = CorrelationIdContext.open(connection)) {
                    RealtimeEventCursorService.Replay replay = cursorService.replayAfter(
                            connection.pathParam("canonical"), state.cursor);
                    if (replay.snapshotRequired()) {
                        sendSnapshot(connection, state, replay.currentCursor(), state.cursor);
                    } else {
                        sendEvents(connection, state, replay);
                    }
                } catch (Exception exception) {
                    LOG.warnf("Falha temporária na leitura do canal da conexão %s", connection.id());
                }
            }
        });
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        connection.userData().remove(STATE);
    }

    private void sendEvents(WebSocketConnection connection, ConnectionState state,
                            RealtimeEventCursorService.Replay replay) {
        for (RealtimeEventCursorService.StoredEvent event : replay.events()) {
            if (!send(connection, WebSocketEvent.from(event.cursor(), event.data(), objectMapper))) return;
            state.cursor = event.cursor();
        }
    }

    private boolean sendSnapshot(WebSocketConnection connection, ConnectionState state,
                                 String cursor, String resumedFrom) {
        String canonical = connection.pathParam("canonical");
        AddressSnapshot data = AddressSnapshot.fromPet(canonical, PetPublicSnapshot.fromCanonical(canonical), resumedFrom);
        if (!send(connection, new WebSocketSnapshot<>(WebSocketCursor.of(cursor), data))) return false;
        state.cursor = cursor;
        return true;
    }

    private boolean send(WebSocketConnection connection, Object frame) {
        try {
            connection.sendTextAndAwait(objectMapper.writeValueAsString(frame));
            return true;
        } catch (Exception exception) {
            // Não avança o cursor: o próximo poll retoma exatamente deste ponto.
            LOG.debugf("Falha ao enviar frame para conexão %s", connection.id());
            return false;
        }
    }

    private static final class ConnectionState {
        String cursor;
        ConnectionState(String cursor) { this.cursor = cursor; }
    }
}
