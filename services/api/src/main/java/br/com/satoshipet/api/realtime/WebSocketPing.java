package br.com.satoshipet.api.realtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Heartbeat enviado pelo servidor, sem cursor nem payload. */
@JsonPropertyOrder({"type"})
public record WebSocketPing() {

    @JsonProperty("type")
    public String type() {
        return "PING";
    }
}
