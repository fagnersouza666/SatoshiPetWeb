package br.com.satoshipet.api.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Mensagem recebida do cliente para responder ao heartbeat ou retomar o canal. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebSocketClientMessage(
        String type,
        WebSocketCursor cursor
) {}
