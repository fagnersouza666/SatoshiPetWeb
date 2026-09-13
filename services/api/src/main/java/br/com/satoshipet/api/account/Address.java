package br.com.satoshipet.api.account;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Endereço Bitcoin canônico monitorado pela plataforma. */
@Entity
@Table(name = "addresses")
public class Address extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    /** Forma canônica do endereço Bitcoin (bech32, p2sh, legacy). */
    @Column(name = "canonical", nullable = false, length = 90, unique = true, updatable = false)
    public String canonical;

    /** Momento em que o endereço foi registrado na plataforma. */
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    protected Address() {
        // Construtor exigido pelo Hibernate ORM.
    }

    /** Cria um registro de endereço com id gerado. */
    public static Address create(String canonical, Instant now) {
        Objects.requireNonNull(canonical, "canonical");
        Objects.requireNonNull(now, "now");

        Address address = new Address();
        address.id = UUID.randomUUID();
        address.canonical = canonical;
        address.createdAt = now;
        return address;
    }

    /** Busca endereço pela forma canônica. */
    public static Optional<Address> findByCanonical(String canonical) {
        return find("canonical", canonical).firstResultOptional();
    }
}
