package br.com.satoshipet.api.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/** Mensagem de estado inicial, com cursor da posição observada. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "cursor", "data"})
public record WebSocketSnapshot<T>(
        WebSocketCursor cursor,
        T data
) {

    public WebSocketSnapshot {
        Objects.requireNonNull(cursor, "cursor não pode ser nulo");
        Objects.requireNonNull(data, "data não pode ser nulo");
    }

    @JsonProperty("type")
    public String type() {
        return "SNAPSHOT";
    }
}
