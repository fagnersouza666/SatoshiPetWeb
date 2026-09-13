package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.Address;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Criatura persistente vinculada a um endereço Bitcoin.
 * Um endereço tem exatamente um pet permanente; não existe morte, reinício
 * ou exclusão (PRD §5).
 */
@Entity
@Table(name = "pets")
public class Pet extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    /**
     * Endereço Bitcoin ao qual o pet pertence.
     * Único por endereço (UNIQUE constraint em addresses.id).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "address_id", nullable = false, updatable = false)
    public Address address;

    /** Conta que criou o pet. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_account_id", nullable = false, updatable = false)
    public Account creatorAccount;

    /** Nome do pet escolhido pelo usuário. */
    @Column(name = "name", nullable = false, length = 100)
    public String name;

    /** Momento de criação (UTC). */
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    /** Momento da última atualização de estado. */
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    protected Pet() {
        // Construtor exigido pelo Hibernate ORM.
    }

    /** Cria um pet com id gerado. */
    public static Pet create(Address address, Account creatorAccount, String name, Instant now) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(creatorAccount, "creatorAccount");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(now, "now");

        Pet pet = new Pet();
        pet.id = UUID.randomUUID();
        pet.address = address;
        pet.creatorAccount = creatorAccount;
        pet.name = name;
        pet.createdAt = now;
        pet.updatedAt = now;
        return pet;
    }

    /** Localiza o pet pelo endereço Bitcoin (no máximo um). */
    public static Optional<Pet> findByAddress(Address address) {
        return find("address", address).firstResultOptional();
    }
}
