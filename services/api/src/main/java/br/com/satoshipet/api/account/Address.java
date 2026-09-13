package br.com.satoshipet.api.account;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Endereço Bitcoin canônico monitorado pela plataforma. */
@Entity
@Table(
        name = "addresses",
        indexes = {
                @Index(
                        name = "uq_addresses_network_canonical",
                        columnList = "network, canonical",
                        unique = true
                )
        }
)
public class Address extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    /** Forma canônica do endereço Bitcoin (bech32, p2sh, legacy). */
    @Column(name = "canonical", nullable = false, length = 90, updatable = false)
    public String canonical;

    /** Rede Bitcoin identificada pelo endereço (mainnet, testnet ou regtest). */
    @Column(name = "network", nullable = false, length = 20, updatable = false)
    public String network;

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

        return create(canonical, detectNetwork(canonical), now);
    }

    /** Cria um endereço com a rede explicitamente identificada. */
    public static Address create(String canonical, String network, Instant now) {
        Objects.requireNonNull(canonical, "canonical");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(now, "now");
        if (canonical.isBlank()) {
            throw new IllegalArgumentException("canonical não pode ser vazio");
        }

        String normalizedNetwork = network.trim().toLowerCase(java.util.Locale.ROOT);
        if (!isSupportedNetwork(normalizedNetwork)) {
            throw new IllegalArgumentException("Rede Bitcoin não suportada: " + network);
        }

        Address address = new Address();
        address.id = UUID.randomUUID();
        address.canonical = canonical;
        address.network = normalizedNetwork;
        address.createdAt = now;
        return address;
    }

    /** Busca endereço pela forma canônica. */
    public static Optional<Address> findByCanonical(String canonical) {
        return find("canonical", canonical).firstResultOptional();
    }

    /** Busca endereço pela chave lógica rede + forma canônica. */
    public static Optional<Address> findByNetworkAndCanonical(String network, String canonical) {
        return find("network = ?1 AND canonical = ?2", network, canonical).firstResultOptional();
    }

    private static String detectNetwork(String canonical) {
        String normalized = canonical.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("bcrt1")) {
            return "regtest";
        }
        if (normalized.startsWith("bc1") || normalized.startsWith("1")
                || normalized.startsWith("3")) {
            return "mainnet";
        }
        if (normalized.startsWith("tb1") || normalized.startsWith("m")
                || normalized.startsWith("n") || normalized.startsWith("2")) {
            return "testnet";
        }
        throw new IllegalArgumentException("Não foi possível identificar a rede do endereço");
    }

    private static boolean isSupportedNetwork(String network) {
        return network.equals("mainnet") || network.equals("testnet") || network.equals("regtest");
    }
}
