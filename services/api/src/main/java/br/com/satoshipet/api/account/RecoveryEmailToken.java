package br.com.satoshipet.api.account;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Verificação dedicada do novo e-mail, inseparável do código de recuperação apresentado. */
@Entity
@Table(name = "recovery_email_tokens")
public class RecoveryEmailToken extends PanacheEntityBase {
    @Id public UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false) public Account account;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recovery_code_id", nullable = false) public RecoveryCode recoveryCode;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) public String tokenHash;
    @Column(name = "new_email", nullable = false, length = 320) public String newEmail;
    @Column(name = "issued_at", nullable = false) public Instant issuedAt;
    @Column(name = "expires_at", nullable = false) public Instant expiresAt;
    @Column(name = "consumed_at") public Instant consumedAt;

    public boolean usableAt(Instant now) {
        return consumedAt == null && !now.isBefore(issuedAt) && now.isBefore(expiresAt);
    }
}
