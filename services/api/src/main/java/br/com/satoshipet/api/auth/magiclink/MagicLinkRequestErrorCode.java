package br.com.satoshipet.api.auth.magiclink;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Códigos estáveis de erro do contrato de solicitação. */
public enum MagicLinkRequestErrorCode {
    INVALID_REQUEST("invalid_request"),
    RATE_LIMITED("rate_limited"),
    REQUEST_UNAVAILABLE("request_unavailable"),
    INTERNAL_ERROR("internal_error");

    private final String value;

    MagicLinkRequestErrorCode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static MagicLinkRequestErrorCode fromValue(String value) {
        for (MagicLinkRequestErrorCode code : values()) {
            if (code.value.equals(value)) {
                return code;
            }
        }
        throw new IllegalArgumentException("Código de erro de magic link desconhecido: " + value);
    }
}
