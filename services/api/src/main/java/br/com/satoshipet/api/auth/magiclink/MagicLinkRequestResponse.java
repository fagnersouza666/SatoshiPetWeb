package br.com.satoshipet.api.auth.magiclink;

import java.util.Objects;

/** Resposta uniforme para uma solicitação aceita. */
public record MagicLinkRequestResponse(
        MagicLinkRequestStatus status,
        String message
) {

    public static final String ACCEPTED_MESSAGE =
            "Se o e-mail informado puder ser usado, você receberá um link para continuar.";

    public MagicLinkRequestResponse {
        Objects.requireNonNull(status, "status não pode ser nulo");
        Objects.requireNonNull(message, "message não pode ser nulo");
    }

    public static MagicLinkRequestResponse accepted() {
        return new MagicLinkRequestResponse(MagicLinkRequestStatus.ACCEPTED, ACCEPTED_MESSAGE);
    }
}
