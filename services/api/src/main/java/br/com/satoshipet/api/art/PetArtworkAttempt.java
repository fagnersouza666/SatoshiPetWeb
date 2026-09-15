package br.com.satoshipet.api.art;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Tentativa de geração de sprites (ART-01). */
@Entity
@Table(
        name = "pet_artwork_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_pet_artwork_attempts_no",
                columnNames = {"artwork_id", "attempt_no"}
        )
)
public class PetArtworkAttempt extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artwork_id", nullable = false, updatable = false)
    public PetArtwork artwork;

    @Column(name = "attempt_no", nullable = false, updatable = false)
    public int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30, updatable = false)
    public ArtAttemptReason reason;

    @Column(name = "valid", nullable = false)
    public boolean valid;

    @Column(name = "rejection_reason", length = 120)
    public String rejectionReason;

    @Column(name = "storage_key_prefix", nullable = false, length = 255, updatable = false)
    public String storageKeyPrefix;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    protected PetArtworkAttempt() {
    }

    public static PetArtworkAttempt create(
            PetArtwork artwork,
            int attemptNo,
            ArtAttemptReason reason,
            boolean valid,
            String rejectionReason,
            String storageKeyPrefix,
            Instant now
    ) {
        Objects.requireNonNull(artwork, "artwork");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(storageKeyPrefix, "storageKeyPrefix");
        Objects.requireNonNull(now, "now");

        PetArtworkAttempt attempt = new PetArtworkAttempt();
        attempt.id = UUID.randomUUID();
        attempt.artwork = artwork;
        attempt.attemptNo = attemptNo;
        attempt.reason = reason;
        attempt.valid = valid;
        attempt.rejectionReason = rejectionReason;
        attempt.storageKeyPrefix = storageKeyPrefix;
        attempt.createdAt = now;
        return attempt;
    }

    public static List<PetArtworkAttempt> listByArtwork(PetArtwork artwork) {
        return list("artwork = ?1 ORDER BY attemptNo ASC", artwork);
    }

    public static int nextAttemptNo(PetArtwork artwork) {
        Integer max = getEntityManager()
                .createQuery(
                        "SELECT MAX(a.attemptNo) FROM PetArtworkAttempt a WHERE a.artwork = :artwork",
                        Integer.class
                )
                .setParameter("artwork", artwork)
                .getSingleResult();
        return max == null ? 1 : max + 1;
    }
}
