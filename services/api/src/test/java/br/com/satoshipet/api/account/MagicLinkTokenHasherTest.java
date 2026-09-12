package br.com.satoshipet.api.account;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MagicLinkTokenHasherTest {

    private final MagicLinkTokenHasher hasher = new MagicLinkTokenHasher();

    @Test
    void createsStableSha256VerifierWithoutReturningSecret() {
        String rawToken = "token-secreto-de-teste";

        String firstHash = hasher.hash(rawToken);

        assertEquals(64, firstHash.length());
        assertEquals(firstHash, hasher.hash(rawToken));
        assertNotEquals(rawToken, firstHash);
    }

    @Test
    void rejectsEmptyToken() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(""));
    }
}
