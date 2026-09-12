package br.com.satoshipet.api.auth.magiclink;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Códigos de validação aplicáveis aos campos da solicitação. */
public enum MagicLinkFieldErrorCode {
    REQUIRED("required"),
    INVALID_FORMAT("invalid_format"),
    TOO_LONG("too_long");

    private final String value;

    MagicLinkFieldErrorCode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static MagicLinkFieldErrorCode fromValue(String value) {
        for (MagicLinkFieldErrorCode code : values()) {
            if (code.value.equals(value)) {
                return code;
            }
        }
        throw new IllegalArgumentException("Código de campo inválido desconhecido: " + value);
    }
}
