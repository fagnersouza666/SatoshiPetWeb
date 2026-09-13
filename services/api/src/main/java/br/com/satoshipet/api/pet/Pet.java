package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.pet.engine.EmotionalState;
import br.com.satoshipet.api.pet.engine.ReserveMath;
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

import java.math.BigDecimal;
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

    /** Conta cuja porção de 24h alimenta este pet (CC-05). Inicia como o criador. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_source_account_id")
    public Account foodSourceAccount;

    /** Última porção positiva aplicada, em sats. */
    @Column(name = "last_positive_portion_sats")
    public Long lastPositivePortionSats;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_positive_portion_origin", length = 40)
    public PortionOrigin lastPositivePortionOrigin;

    /** Reserva de alimentação em horas (NUMERIC, nunca float — CC-10). */
    @Column(name = "reserve_hours", nullable = false, precision = 20, scale = 10)
    public BigDecimal reserveHours;

    /** Instant em que a reserva chegou a zero. */
    @Column(name = "reserve_depleted_at")
    public Instant reserveDepletedAt;

    /** Última avaliação do motor de estados (UTC). */
    @Column(name = "last_evaluated_at", nullable = false)
    public Instant lastEvaluatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "emotional_state", nullable = false, length = 20)
    public EmotionalState emotionalState;

    @Enumerated(EnumType.STRING)
    @Column(name = "presentation", nullable = false, length = 20)
    public PetPresentation presentation;

    /** Nascimento da criatura (saída do ovo). */
    @Column(name = "born_at")
    public Instant bornAt;

    /** Início da carência de saldo confirmado zero (retorno ao ovo). */
    @Column(name = "zero_balance_since")
    public Instant zeroBalanceSince;

    /** Sem porção de referência ainda (aguardando plano do criador). */
    @Column(name = "awaiting_reference", nullable = false)
    public boolean awaitingReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "artwork_status", nullable = false, length = 20)
    public ArtworkStatus artworkStatus;

    @Column(name = "last_returned_to_egg_at")
    public Instant lastReturnedToEggAt;

    @Column(name = "last_reappeared_at")
    public Instant lastReappearedAt;

    protected Pet() {
        // Construtor exigido pelo Hibernate ORM.
    }

    /** Cria um pet com id gerado e estado inicial do motor (ovo, reserva 0). */
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
        pet.foodSourceAccount = creatorAccount;
        pet.reserveHours = BigDecimal.ZERO.setScale(ReserveMath.SCALE);
        pet.lastEvaluatedAt = now;
        pet.emotionalState = EmotionalState.ALIMENTADO;
        pet.presentation = PetPresentation.EGG;
        pet.awaitingReference = true;
        pet.artworkStatus = ArtworkStatus.NONE;
        return pet;
    }

    /** Localiza o pet pelo endereço Bitcoin (no máximo um). */
    public static Optional<Pet> findByAddress(Address address) {
        return find("address", address).firstResultOptional();
    }
}
