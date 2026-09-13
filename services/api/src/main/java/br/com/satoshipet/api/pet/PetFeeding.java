package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.pet.engine.ReserveMath;
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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Alimentação aplicada a um pet a partir de um recebimento lógico.
 * Unicidade (pet, recebimento) impede duplicar RBF/reorg (CA-017).
 */
@Entity
@Table(
        name = "pet_feedings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_pet_feedings_pet_receipt",
                        columnNames = {"pet_id", "logical_receipt_id"}
                )
        },
        indexes = {
                @Index(name = "idx_pet_feedings_pet", columnList = "pet_id")
        }
)
public class PetFeeding extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false, updatable = false)
    public Pet pet;

    @Column(name = "logical_receipt_id", nullable = false, updatable = false)
    public UUID logicalReceiptId;

    @Column(name = "amount_sats", nullable = false)
    public long amountSats;

    @Column(name = "portion_sats", nullable = false)
    public long portionSats;

    @Column(name = "duration_hours", nullable = false, precision = 20, scale = 10)
    public BigDecimal durationHours;

    @Column(name = "effective_at", nullable = false, updatable = false)
    public Instant effectiveAt;

    @Column(name = "rule_version", nullable = false, length = 40, updatable = false)
    public String ruleVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public FeedingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 40, updatable = false)
    public FeedingOrigin origin;

    @Column(name = "presentable", nullable = false)
    public boolean presentable;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    protected PetFeeding() {
        // Construtor exigido pelo Hibernate ORM.
    }

    public static PetFeeding create(
            Pet pet,
            UUID logicalReceiptId,
            long amountSats,
            long portionSats,
            BigDecimal durationHours,
            Instant effectiveAt,
            FeedingStatus status,
            FeedingOrigin origin,
            boolean presentable,
            Instant now
    ) {
        Objects.requireNonNull(pet, "pet");
        Objects.requireNonNull(logicalReceiptId, "logicalReceiptId");
        Objects.requireNonNull(durationHours, "durationHours");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(now, "now");

        PetFeeding feeding = new PetFeeding();
        feeding.id = UUID.randomUUID();
        feeding.pet = pet;
        feeding.logicalReceiptId = logicalReceiptId;
        feeding.amountSats = amountSats;
        feeding.portionSats = portionSats;
        feeding.durationHours = durationHours;
        feeding.effectiveAt = effectiveAt;
        feeding.ruleVersion = ReserveMath.RULE_VERSION;
        feeding.status = status;
        feeding.origin = origin;
        feeding.presentable = presentable;
        feeding.createdAt = now;
        feeding.updatedAt = now;
        return feeding;
    }

    public static Optional<PetFeeding> findByPetAndReceipt(Pet pet, UUID logicalReceiptId) {
        return find("pet = ?1 AND logicalReceiptId = ?2", pet, logicalReceiptId).firstResultOptional();
    }

    public static List<PetFeeding> listByPet(Pet pet) {
        return list("pet", pet);
    }
}
