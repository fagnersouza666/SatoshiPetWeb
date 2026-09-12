package br.com.satoshipet.api.account;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MagicLinkTokenPersistenceTest {

    private static final Duration CONFIGURED_TTL = Duration.ofMinutes(15);

    @Inject
    MagicLinkTokenService service;

    @Inject
    MagicLinkTokenRepository repository;

    @Inject
    MagicLinkTokenHasher hasher;

    @Test
    void persistsHashAndConfiguredExpirationWithoutPlaintext() {
        String rawToken = "token-" + UUID.randomUUID();
        Instant issuedAt = Instant.parse("2026-09-12T15:00:00Z");

        MagicLinkToken issued = service.issue("pessoa@example.com", rawToken, issuedAt);
        Optional<MagicLinkToken> persisted = repository.findByIdOptional(issued.getId());

        assertTrue(persisted.isPresent());
        assertNotEquals(rawToken, persisted.get().getTokenHash());
        assertEquals(hasher.hash(rawToken), persisted.get().getTokenHash());
        assertEquals(issuedAt, persisted.get().getIssuedAt());
        assertEquals(issuedAt.plus(CONFIGURED_TTL), persisted.get().getExpiresAt());
        assertNull(persisted.get().getConsumedAt());
        assertNotNull(persisted.get().getEmail());
    }

    @Test
    void consumesOnlyOnce() {
        String rawToken = "token-" + UUID.randomUUID();
        Instant issuedAt = Instant.parse("2026-09-12T15:10:00Z");
        Instant consumedAt = issuedAt.plusSeconds(30);
        service.issue("uso-unico@example.com", rawToken, issuedAt);

        Optional<MagicLinkToken> firstConsumption = service.consume(rawToken, consumedAt);
        Optional<MagicLinkToken> secondConsumption = service.consume(rawToken, consumedAt.plusSeconds(1));

        assertTrue(firstConsumption.isPresent());
        assertEquals(consumedAt, firstConsumption.get().getConsumedAt());
        assertFalse(secondConsumption.isPresent());
    }

    @Test
    void rejectsTokenAtExpirationInstant() {
        String rawToken = "token-" + UUID.randomUUID();
        Instant issuedAt = Instant.parse("2026-09-12T15:20:00Z");
        service.issue("expirado@example.com", rawToken, issuedAt);

        Optional<MagicLinkToken> consumption = service.consume(
                rawToken,
                issuedAt.plus(CONFIGURED_TTL)
        );

        assertFalse(consumption.isPresent());
    }

    @Test
    void rejectsTokenBeforeIssueInstant() {
        String rawToken = "token-" + UUID.randomUUID();
        Instant issuedAt = Instant.parse("2026-09-12T15:30:00Z");
        service.issue("futuro@example.com", rawToken, issuedAt);

        Optional<MagicLinkToken> consumption = service.consume(
                rawToken,
                issuedAt.minusSeconds(1)
        );

        assertFalse(consumption.isPresent());
    }
}
