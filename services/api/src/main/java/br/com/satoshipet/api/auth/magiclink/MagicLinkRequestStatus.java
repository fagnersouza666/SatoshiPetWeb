package br.com.satoshipet.api.auth.magiclink;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Estado público da solicitação, sem indicar se o e-mail já possui conta. */
public enum MagicLinkRequestStatus {
    ACCEPTED("accepted");

    private final String value;

    MagicLinkRequestStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static MagicLinkRequestStatus fromValue(String value) {
        for (MagicLinkRequestStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Status de solicitação de magic link desconhecido: " + value);
    }
}
