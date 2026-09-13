package br.com.satoshipet.api.account;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Sessão autenticada de uma conta. */
@Entity
@Table(
        name = "sessions",
        indexes = {
                @Index(name = "idx_sessions_account",    columnList = "account_id"),
                @Index(name = "idx_sessions_expires_at", columnList = "expires_at"),
                @Index(name = "uq_sessions_token_hash",  columnList = "token_hash", unique = true)
        }
)
public class Session extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    public Account account;

    /** Hash SHA-256 do token de sessão bruto (nunca armazenar o token em si). */
    @Column(name = "token_hash", nullable = false, length = 64, unique = true, updatable = false)
    public String tokenHash;

    /**
     * Hash SHA-256 do token CSRF gerado para esta sessão.
     * Validado pelo {@code CsrfValidationFilter} em mutations (POST/PATCH/DELETE).
     */
    @Column(name = "csrf_token_hash", length = 64)
    public String csrfTokenHash;

    /** Momento em que a sessão foi criada. */
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    /** Momento de expiração natural da sessão. */
    @Column(name = "expires_at", nullable = false, updatable = false)
    public Instant expiresAt;

    /** Preenchido ao invalidar a sessão antecipadamente (logout). */
    @Column(name = "invalidated_at")
    public Instant invalidatedAt;

    /** Agente do cliente que criou a sessão (para auditoria). */
    @Column(name = "user_agent", columnDefinition = "TEXT")
    public String userAgent;

    /** IP de origem (IPv4 ou IPv6). */
    @Column(name = "ip_address", length = 45)
    public String ipAddress;

    protected Session() {
        // Construtor exigido pelo Hibernate ORM.
    }

    /** Cria uma sessão com id gerado. */
    public static Session create(
            Account account,
            String tokenHash,
            String csrfTokenHash,
            Instant createdAt,
            Instant expiresAt,
            String userAgent,
            String ipAddress
    ) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt deve ser posterior a createdAt");
        }

        Session session = new Session();
        session.id = UUID.randomUUID();
        session.account = account;
        session.tokenHash = tokenHash;
        session.csrfTokenHash = csrfTokenHash;
        session.createdAt = createdAt;
        session.expiresAt = expiresAt;
        session.userAgent = userAgent;
        session.ipAddress = ipAddress;
        return session;
    }

    /** Verifica se a sessão ainda é válida no instante fornecido. */
    public boolean isValidAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return invalidatedAt == null && now.isBefore(expiresAt);
    }

    /** Invalida a sessão antecipadamente (logout). */
    public void invalidate(Instant now) {
        Objects.requireNonNull(now, "now");
        if (invalidatedAt != null) {
            return; // já invalidada — idempotente
        }
        this.invalidatedAt = now;
    }

    /** Busca sessão pelo hash do token bruto. */
    public static Optional<Session> findByTokenHash(String tokenHash) {
        return find("tokenHash", tokenHash).firstResultOptional();
    }

    /** Busca sessão ativa pelo hash do token bruto. */
    public static Optional<Session> findActiveByTokenHash(String tokenHash, Instant now) {
        return find(
                "tokenHash = ?1 AND invalidatedAt IS NULL AND expiresAt > ?2",
                tokenHash, now
        ).firstResultOptional();
    }

    /** Invalida todas as sessões ativas de uma conta (logout geral). */
    public static long invalidateAllForAccount(UUID accountId, Instant now) {
        return update(
                "invalidatedAt = ?1 WHERE account.id = ?2 AND invalidatedAt IS NULL",
                now, accountId
        );
    }
}
