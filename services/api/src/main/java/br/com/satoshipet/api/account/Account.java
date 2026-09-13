package br.com.satoshipet.api.account;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Conta de usuário registrada na plataforma. */
@Entity
@Table(
        name = "accounts",
        indexes = {
                @Index(name = "idx_accounts_email", columnList = "email")
        }
)
public class Account extends PanacheEntityBase {

    /** Janela absoluta permitida para troca do endereço inicial. */
    public static final Duration ADDRESS_CHANGE_WINDOW = Duration.ofHours(72);

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    /** Endereço de e-mail único do titular. */
    @Column(name = "email", nullable = false, length = 320)
    public String email;

    /** Momento de criação da conta (UTC). */
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    /** Fuso horário IANA do titular (ex: "America/Sao_Paulo"). */
    @Column(name = "timezone", nullable = false, length = 50)
    public String timezone;

    /** Localidade para formatação de UI (ex: "pt-BR"). */
    @Column(name = "locale", nullable = false, length = 10)
    public String locale;

    /**
     * Prazo até o qual a troca de endereço ainda é permitida.
     * Nulo antes de qualquer vínculo; imutável após as 72h da primeira vinculação (CA-004).
     */
    @Column(name = "address_change_deadline", updatable = false)
    public Instant addressChangeDeadline;

    protected Account() {
        // Construtor exigido pelo Hibernate ORM.
    }

    /** Cria uma conta nova com id gerado e timestamp fornecido. */
    public static Account create(String email, String timezone, String locale, Instant now) {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(timezone, "timezone");
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(now, "now");

        Account account = new Account();
        account.id = UUID.randomUUID();
        account.email = email;
        account.timezone = timezone;
        account.locale = locale;
        account.createdAt = now;
        account.addressChangeDeadline = now.plus(ADDRESS_CHANGE_WINDOW);
        return account;
    }

    /** Localiza conta pelo e-mail. */
    public static Account findByEmail(String email) {
        return find("email", email).firstResult();
    }
}
