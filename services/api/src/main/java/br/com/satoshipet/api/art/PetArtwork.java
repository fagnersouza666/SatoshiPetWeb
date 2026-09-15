package br.com.satoshipet.api.art;

import br.com.satoshipet.api.pet.Pet;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Metadados versionados da arte do pet (ART-01). Um registro por pet.
 * O prompt privado nunca é exposto em DTOs ou logs.
 */
@Entity
@Table(
        name = "pet_artworks",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_pet_artworks_pet",
                columnNames = "pet_id"
        )
)
public class PetArtwork extends PanacheEntityBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false, updatable = false)
    public Pet pet;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_status", nullable = false, length = 30)
    public ArtGenerationStatus generationStatus;

    @Column(name = "frozen_context", nullable = false, columnDefinition = "TEXT")
    public String frozenContextJson;

    @Column(name = "prompt_private", nullable = false, columnDefinition = "TEXT")
    public String promptPrivate;

    @Column(name = "model_id", nullable = false, length = 80)
    public String modelId;

    @Column(name = "seed", length = 80)
    public String seed;

    @Column(name = "asset_version", nullable = false)
    public int assetVersion;

    @Column(name = "voluntary_regen_used", nullable = false)
    public boolean voluntaryRegenUsed;

    @Column(name = "technical_attempt_count", nullable = false)
    public int technicalAttemptCount;

    @Column(name = "last_failure_code", length = 60)
    public String lastFailureCode;

    @Column(name = "last_failure_at")
    public Instant lastFailureAt;

    @Column(name = "next_retry_at")
    public Instant nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    protected PetArtwork() {
    }

    public static PetArtwork createPending(Pet pet, FrozenGenerationContext context, String prompt, Instant now) {
        Objects.requireNonNull(pet, "pet");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(now, "now");

        PetArtwork artwork = new PetArtwork();
        artwork.id = UUID.randomUUID();
        artwork.pet = pet;
        artwork.generationStatus = ArtGenerationStatus.GENERATING;
        artwork.frozenContextJson = serializeContext(context);
        artwork.promptPrivate = prompt;
        artwork.modelId = "stub-v1";
        artwork.seed = pet.address.canonical;
        artwork.assetVersion = 0;
        artwork.voluntaryRegenUsed = false;
        artwork.technicalAttemptCount = 0;
        artwork.createdAt = now;
        artwork.updatedAt = now;
        return artwork;
    }

    public FrozenGenerationContext frozenContext() {
        try {
            return MAPPER.readValue(frozenContextJson, FrozenGenerationContext.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Contexto congelado inválido para artwork " + id, e);
        }
    }

    public static Optional<PetArtwork> findByPet(Pet pet) {
        return find("pet", pet).firstResultOptional();
    }

    public static Optional<PetArtwork> findByPetId(UUID petId) {
        return find("pet.id", petId).firstResultOptional();
    }

    private static String serializeContext(FrozenGenerationContext context) {
        try {
            return MAPPER.writeValueAsString(context);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar contexto congelado", e);
        }
    }
}
