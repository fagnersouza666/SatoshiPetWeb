package br.com.satoshipet.api.realtime;

import java.util.Objects;

/** Snapshot mínimo do canal privado de uma conta. */
public record AccountSnapshot(String accountId) {

    public AccountSnapshot {
        Objects.requireNonNull(accountId, "accountId não pode ser nulo");
    }
}
