package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Snapshot imutável da porção de referência de 24h de um pet (CC-05). */
@Entity
@Table(
        name = "pet_reference_portions",
        indexes = {
                @Index(
                        name = "idx_pet_reference_portions_pet_valid_from",
                        columnList = "pet_id, valid_from"
                )
        }
)
public class PetReferencePortion extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false, updatable = false)
    public Pet pet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    public Account sourceAccount;

    @Column(name = "portion_sats", nullable = false)
    public long portionSats;

    @Column(name = "valid_from", nullable = false, updatable = false)
    public Instant validFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 40, updatable = false)
    public PortionOrigin origin;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    protected PetReferencePortion() {
        // Construtor exigido pelo Hibernate ORM.
    }

    public static PetReferencePortion create(
            Pet pet,
            Account sourceAccount,
            long portionSats,
            Instant validFrom,
            PortionOrigin origin,
            Instant now
    ) {
        Objects.requireNonNull(pet, "pet");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(now, "now");

        PetReferencePortion portion = new PetReferencePortion();
        portion.id = UUID.randomUUID();
        portion.pet = pet;
        portion.sourceAccount = sourceAccount;
        portion.portionSats = portionSats;
        portion.validFrom = validFrom;
        portion.origin = origin;
        portion.createdAt = now;
        return portion;
    }

    /** Porção vigente: maior {@code validFrom} do pet. */
    public static Optional<PetReferencePortion> latestForPet(Pet pet) {
        return find("pet = ?1 ORDER BY validFrom DESC", pet).firstResultOptional();
    }

    /**
     * Snapshot mais recente da fonte indicada
     * ({@code validFrom} DESC, {@code createdAt} DESC, {@code id} DESC).
     */
    public static Optional<PetReferencePortion> latestForPetAndSource(Pet pet, Account source) {
        Objects.requireNonNull(pet, "pet");
        Objects.requireNonNull(source, "source");
        return find(
                "pet = ?1 AND sourceAccount = ?2 AND portionSats > 0 "
                        + "ORDER BY validFrom DESC, createdAt DESC, id DESC",
                pet,
                source
        ).firstResultOptional();
    }
}
