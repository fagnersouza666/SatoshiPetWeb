package br.com.satoshipet.api.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Estado público mínimo enviado no snapshot do canal de um endereço.
 *
 * <p>O record funciona como allowlist estrutural: somente endereço, estado e
 * a origem opcional da retomada podem atravessar a serialização.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddressSnapshot(
        String address,
        String state,
        String resumedFrom
) {

    public AddressSnapshot {
        Objects.requireNonNull(address, "address não pode ser nulo");
        Objects.requireNonNull(state, "state não pode ser nulo");
    }

    public static AddressSnapshot initial(String address, String state) {
        return new AddressSnapshot(address, state, null);
    }

    public static AddressSnapshot resumed(String address, String state, String resumedFrom) {
        return new AddressSnapshot(address, state, resumedFrom);
    }
}
