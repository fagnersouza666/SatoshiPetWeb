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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Vínculo entre uma conta e um endereço Bitcoin. */
@Entity
@Table(
        name = "account_address_bindings",
        indexes = {
                @Index(name = "idx_account_address_bindings_account", columnList = "account_id"),
                @Index(name = "idx_account_address_bindings_address", columnList = "address_id")
        }
)
public class AccountAddressBinding extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    public Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "address_id", nullable = false, updatable = false)
    public Address address;

    /** Momento em que o vínculo foi estabelecido. */
    @Column(name = "bound_at", nullable = false, updatable = false)
    public Instant boundAt;

    /**
     * Momento em que o vínculo foi desfeito (nullable).
     * Null indica vínculo ativo; preenchido na troca de endereço.
     */
    @Column(name = "unbound_at")
    public Instant unboundAt;

    /** Indica se este é o endereço principal da conta. */
    @Column(name = "is_primary", nullable = false)
    public boolean primary;

    protected AccountAddressBinding() {
        // Construtor exigido pelo Hibernate ORM.
    }

    /** Cria um vínculo com id gerado. */
    public static AccountAddressBinding create(
            Account account, Address address, boolean primary, Instant now
    ) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(now, "now");

        AccountAddressBinding binding = new AccountAddressBinding();
        binding.id = UUID.randomUUID();
        binding.account = account;
        binding.address = address;
        binding.primary = primary;
        binding.boundAt = now;
        return binding;
    }

    /** Retorna todos os vínculos de uma conta. */
    public static List<AccountAddressBinding> findByAccount(Account account) {
        return list("account", account);
    }

    /** Retorna o vínculo primário ativo de uma conta, se existir. */
    public static Optional<AccountAddressBinding> findActivePrimary(Account account) {
        return find(
                "account = ?1 AND primary = true AND unboundAt IS NULL",
                account
        ).firstResultOptional();
    }

    /** Retorna o vínculo histórico ou ativo entre conta e endereço. */
    public static Optional<AccountAddressBinding> findByAccountAndAddress(
            Account account, Address address
    ) {
        return find("account = ?1 AND address = ?2", account, address).firstResultOptional();
    }

    /** Retorna vínculo (ativo ou histórico) da conta para endereço canônico. */
    public static Optional<AccountAddressBinding> findByAccountAndCanonical(
            Account account, String canonical
    ) {
        return find("account = ?1 AND address.canonical = ?2", account, canonical)
                .firstResultOptional();
    }

    /** Reativa um vínculo desfeito como primário (CC-03 — troca de volta ao endereço anterior). */
    public void rebindAsPrimary(Instant now) {
        Objects.requireNonNull(now, "now");
        this.unboundAt = null;
        this.primary = true;
    }

    /** Marca o vínculo como desfeito. Idempotente. */
    public void unbind(Instant now) {
        Objects.requireNonNull(now, "now");
        if (this.unboundAt == null) {
            this.unboundAt = now;
            this.primary = false;
        }
    }
}
