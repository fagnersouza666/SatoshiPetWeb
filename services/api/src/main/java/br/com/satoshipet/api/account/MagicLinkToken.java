package br.com.satoshipet.api.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Registro pendente de verificação de identidade por magic link. */
@Entity
@Table(
        name = "magic_link_tokens",
        indexes = {
                @Index(name = "idx_magic_link_tokens_expires_at", columnList = "expires_at"),
                @Index(name = "idx_magic_link_tokens_email", columnList = "email")
        }
)
public class MagicLinkToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 320, updatable = false)
    private String email;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true, updatable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected MagicLinkToken() {
        // Construtor exigido pelo Hibernate ORM.
    }

    private MagicLinkToken(
            UUID id,
            String email,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = validateEmail(email);
        this.tokenHash = validateHash(tokenHash);
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt deve ser posterior a issuedAt");
        }
    }

    public static MagicLinkToken issue(
            String email,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt
    ) {
        return new MagicLinkToken(UUID.randomUUID(), email, tokenHash, issuedAt, expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public boolean isUsableAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return consumedAt == null
                && !now.isBefore(issuedAt)
                && now.isBefore(expiresAt);
    }

    void markConsumed(Instant consumedAt) {
        Objects.requireNonNull(consumedAt, "consumedAt");
        if (!isUsableAt(consumedAt)) {
            throw new IllegalStateException("magic link já consumido ou expirado");
        }
        this.consumedAt = consumedAt;
    }

    private static String validateEmail(String email) {
        Objects.requireNonNull(email, "email");
        if (email.isBlank() || email.length() > 320) {
            throw new IllegalArgumentException("email deve ter entre 1 e 320 caracteres");
        }
        return email;
    }

    private static String validateHash(String tokenHash) {
        Objects.requireNonNull(tokenHash, "tokenHash");
        if (!tokenHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("tokenHash deve ser um SHA-256 em hexadecimal");
        }
        return tokenHash;
    }
}
