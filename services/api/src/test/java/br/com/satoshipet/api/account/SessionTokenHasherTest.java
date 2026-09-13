package br.com.satoshipet.api.account;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionTokenHasherTest {

    private final SessionTokenHasher hasher = new SessionTokenHasher();

    @Test
    void produzHashSha256De64Caracteres() {
        String hash = hasher.hash("token-de-sessao-teste");
        assertEquals(64, hash.length());
    }

    @Test
    void hashEstavelParaMesmoToken() {
        String token = "token-repetido";
        assertEquals(hasher.hash(token), hasher.hash(token));
    }

    @Test
    void hashDiferentesTokensDiferentes() {
        assertNotEquals(hasher.hash("tokenA"), hasher.hash("tokenB"));
    }

    @Test
    void hashNaoRetornaTokenBruto() {
        String raw = "meu-token-secreto";
        assertNotEquals(raw, hasher.hash(raw));
    }

    @Test
    void rejeitaTokenVazio() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(""));
    }

    @Test
    void rejeitaTokenNulo() {
        assertThrows(NullPointerException.class, () -> hasher.hash(null));
    }
}
