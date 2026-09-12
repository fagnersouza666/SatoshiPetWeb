package br.com.satoshipet.api.auth.magiclink;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dados aceitos para iniciar o fluxo de autenticação por magic link. */
public record MagicLinkRequest(
        @NotBlank(message = "Informe o e-mail.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = MAX_EMAIL_LENGTH, message = "O e-mail deve ter no máximo 254 caracteres.")
        String email
) {

    /** Limite do endereço completo conforme o limite prático de e-mail da API. */
    public static final int MAX_EMAIL_LENGTH = 254;
}
