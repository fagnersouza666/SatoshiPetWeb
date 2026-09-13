package br.com.satoshipet.api.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * Evento incremental público enviado no canal WebSocket.
 *
 * <p>O payload é um {@link JsonNode} para evitar serialização direta de
 * entidades ou objetos privados. A redação do evento ocorre antes de o
 * consumidor do outbox chamar este DTO; a conversão centralizada mantém o
 * envelope do protocolo estável para mapas, textos e árvores JSON.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "cursor", "data"})
public record WebSocketEvent(
        WebSocketCursor cursor,
        JsonNode data
) {

    public WebSocketEvent {
        Objects.requireNonNull(cursor, "cursor não pode ser nulo");
        Objects.requireNonNull(data, "data não pode ser nulo");
    }

    /** Constrói o DTO a partir do payload já redigido do evento. */
    public static WebSocketEvent from(String cursor, Object data, ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "objectMapper não pode ser nulo");
        JsonNode payload = data instanceof JsonNode node
                ? node
                : objectMapper.valueToTree(data);
        return new WebSocketEvent(WebSocketCursor.of(cursor), payload);
    }

    @JsonProperty("type")
    public String type() {
        return "EVENT";
    }
}
