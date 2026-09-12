package br.com.satoshipet.api.auth.magiclink;

import java.util.Objects;

/** Erro de validação sem repetir o valor privado informado pelo usuário. */
public record MagicLinkFieldError(
        String field,
        MagicLinkFieldErrorCode code,
        String message
) {

    public MagicLinkFieldError {
        Objects.requireNonNull(field, "field não pode ser nulo");
        Objects.requireNonNull(code, "code não pode ser nulo");
        Objects.requireNonNull(message, "message não pode ser nulo");
    }
}
