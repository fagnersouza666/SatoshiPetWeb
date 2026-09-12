package br.com.satoshipet.api.auth.magiclink;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Objects;

/** Envelope de erro público para a solicitação de magic link. */
public record MagicLinkErrorResponse(
        MagicLinkRequestErrorCode code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<MagicLinkFieldError> fieldErrors
) {

    public MagicLinkErrorResponse {
        Objects.requireNonNull(code, "code não pode ser nulo");
        Objects.requireNonNull(message, "message não pode ser nulo");
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static MagicLinkErrorResponse invalidRequest(List<MagicLinkFieldError> fieldErrors) {
        return new MagicLinkErrorResponse(
                MagicLinkRequestErrorCode.INVALID_REQUEST,
                "A solicitação contém dados inválidos.",
                fieldErrors
        );
    }

    public static MagicLinkErrorResponse rateLimited() {
        return new MagicLinkErrorResponse(
                MagicLinkRequestErrorCode.RATE_LIMITED,
                "Muitas solicitações. Tente novamente mais tarde.",
                List.of()
        );
    }

    public static MagicLinkErrorResponse requestUnavailable() {
        return new MagicLinkErrorResponse(
                MagicLinkRequestErrorCode.REQUEST_UNAVAILABLE,
                "Não foi possível iniciar a solicitação agora. Tente novamente mais tarde.",
                List.of()
        );
    }

    public static MagicLinkErrorResponse internalError() {
        return new MagicLinkErrorResponse(
                MagicLinkRequestErrorCode.INTERNAL_ERROR,
                "Não foi possível concluir a solicitação agora. Tente novamente mais tarde.",
                List.of()
        );
    }
}
