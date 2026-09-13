package br.com.satoshipet.api.account;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Código de recuperação de conta persistido somente como hash.
 *
 * <p>Apresentado ao usuário uma única vez na criação; consumido uma única
 * vez no fluxo de recuperação. Nunca armazena o código em texto claro.</p>
 */
@Entity
@Table(name = "recovery_codes")
public class RecoveryCode extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    public Account account;

    /** Hash SHA-256 do código bruto. Único por conta. */
    @Column(name = "code_hash", nullable = false, length = 64, unique = true, updatable = false)
    public String codeHash;

    /** Momento em que o código foi gerado. */
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    /** Preenchido ao usar o código para recuperação. */
    @Column(name = "used_at")
    public Instant usedAt;

    protected RecoveryCode() {
        // Construtor exigido pelo Hibernate ORM.
    }

    /** Cria um código de recuperação com id gerado. */
    public static RecoveryCode create(Account account, String codeHash, Instant now) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(codeHash, "codeHash");
        Objects.requireNonNull(now, "now");

        RecoveryCode code = new RecoveryCode();
        code.id = UUID.randomUUID();
        code.account = account;
        code.codeHash = codeHash;
        code.createdAt = now;
        return code;
    }

    /** Verifica se o código ainda pode ser usado. */
    public boolean isUsable() {
        return usedAt == null;
    }

    /** Marca o código como usado. Idempotente. */
    public void markUsed(Instant now) {
        Objects.requireNonNull(now, "now");
        if (this.usedAt == null) {
            this.usedAt = now;
        }
    }

    /** Busca código de recuperação pelo hash. */
    public static Optional<RecoveryCode> findByCodeHash(String codeHash) {
        return find("codeHash", codeHash).firstResultOptional();
    }

    /** Remove todos os códigos de uma conta (para regeneração). */
    public static long deleteByAccount(Account account) {
        return delete("account", account);
    }

    /** Lista códigos ativos de uma conta. */
    public static List<RecoveryCode> findActiveByAccount(Account account) {
        return list("account = ?1 AND usedAt IS NULL", account);
    }
}
