package br.com.satoshipet.api.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Envelope de mensagem trocado no canal WebSocket.
 *
 * <p>Protocolo snapshot + cursor:
 * <ul>
 *   <li>{@code SNAPSHOT} — enviado na abertura; contém estado atual e cursor.</li>
 *   <li>{@code EVENT}    — evento incremental com cursor atualizado.</li>
 *   <li>{@code PING}     — heartbeat enviado pelo servidor a cada 30 s.</li>
 *   <li>{@code PONG}     — resposta do cliente ao PING.</li>
 *   <li>{@code RECONNECT}— enviado pelo cliente para reconectar com cursor salvo.</li>
 * </ul>
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebSocketMessage(
        String type,
        String cursor,
        Object data
) {
    public static WebSocketMessage snapshot(String cursor, Object data) {
        return new WebSocketMessage("SNAPSHOT", cursor, data);
    }

    public static WebSocketMessage event(String cursor, Object data) {
        return new WebSocketMessage("EVENT", cursor, data);
    }

    public static WebSocketMessage ping() {
        return new WebSocketMessage("PING", null, null);
    }

    public static WebSocketMessage pong() {
        return new WebSocketMessage("PONG", null, null);
    }
}
