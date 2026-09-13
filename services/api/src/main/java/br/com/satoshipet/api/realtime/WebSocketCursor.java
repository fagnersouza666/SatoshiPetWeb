package br.com.satoshipet.api.realtime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/**
 * Cursor opaco usado pelo protocolo de mensagens em tempo real.
 *
 * <p>O valor permanece uma string no JSON para que o cliente possa armazená-lo
 * sem depender da representação interna da sequência. A validação numérica é
 * responsabilidade do {@link RealtimeEventCursorService}; assim, um cursor
 * inválido recebido na reconexão continua sendo tratado como uma solicitação
 * sem eventos, sem derrubar o canal.</p>
 */
public record WebSocketCursor(String value) {

    public WebSocketCursor {
        Objects.requireNonNull(value, "cursor não pode ser nulo");
        if (value.isBlank()) {
            throw new IllegalArgumentException("cursor não pode ser vazio");
        }
    }

    public static WebSocketCursor of(String value) {
        return new WebSocketCursor(value);
    }

    public static WebSocketCursor initial() {
        return new WebSocketCursor("0");
    }

    /** Mantém o cursor como string no contrato JSON. */
    @JsonValue
    public String serializedValue() {
        return value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static WebSocketCursor fromJson(String value) {
        return new WebSocketCursor(value);
    }
}
