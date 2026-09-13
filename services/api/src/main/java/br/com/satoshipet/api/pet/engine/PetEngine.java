package br.com.satoshipet.api.pet.engine;

import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.btc.BitcoinTransaction;
import br.com.satoshipet.api.btc.LogicalReceipt;
import br.com.satoshipet.api.pet.ArtworkStatus;
import br.com.satoshipet.api.pet.FeedingOrigin;
import br.com.satoshipet.api.pet.FeedingStatus;
import br.com.satoshipet.api.pet.Pet;
import br.com.satoshipet.api.pet.PetFeeding;
import br.com.satoshipet.api.pet.PetLifecyclePort;
import br.com.satoshipet.api.pet.PetPresentation;
import br.com.satoshipet.api.pet.PetReferencePortionPort;
import br.com.satoshipet.api.pet.PetReferencePortionPort.ResolvedPortion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Motor persistente de alimentação e reserva do pet (CA-015, CA-017, CC-11, CC-14).
 *
 * <p>Somente recebimentos on-chain alimentam o pet. Compra declarada, sugestão
 * DCA ou notificação nunca devem chamar estes handlers.</p>
 */
@ApplicationScoped
public class PetEngine implements PetLifecyclePort {

    private static final Logger LOG = Logger.getLogger(PetEngine.class);
    private static final BigDecimal ZERO_HOURS = BigDecimal.ZERO.setScale(ReserveMath.SCALE);

    private final PetReferencePortionPort portionPort;

    @Inject
    public PetEngine(PetReferencePortionPort portionPort) {
        this.portionPort = portionPort;
    }

    @Override
    @Transactional
    public void onReceiptObserved(
            UUID petId,
            UUID logicalReceiptId,
            long amountSats,
            boolean confirmed,
            Instant observedAt
    ) {
        Objects.requireNonNull(logicalReceiptId, "logicalReceiptId");
        Objects.requireNonNull(observedAt, "observedAt");
        Pet pet = loadPet(petId);
        Optional<ResolvedPortion> portion = portionPort.currentPositivePortion(petId);
        if (portion.isEmpty()) {
            return;
        }
        Optional<PetFeeding> existing = PetFeeding.findByPetAndReceipt(pet, logicalReceiptId);
        if (existing.isPresent()) {
            handleExistingObservation(pet, existing.get(), portion.get(), amountSats, confirmed, observedAt);
            return;
        }
        applyNewReceipt(pet, logicalReceiptId, amountSats, confirmed, observedAt, portion.get());
    }

    @Override
    @Transactional
    public void onReceiptConfirmed(UUID petId, UUID logicalReceiptId, long amountSats, Instant confirmedAt) {
        Objects.requireNonNull(logicalReceiptId, "logicalReceiptId");
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        Pet pet = loadPet(petId);
        Optional<ResolvedPortion> portion = portionPort.currentPositivePortion(petId);
        if (portion.isEmpty()) {
            return;
        }
        Optional<PetFeeding> existing = PetFeeding.findByPetAndReceipt(pet, logicalReceiptId);
        if (existing.isEmpty()) {
            applyNewReceipt(pet, logicalReceiptId, amountSats, true, confirmedAt, portion.get());
            return;
        }
        confirmExisting(pet, existing.get(), portion.get(), amountSats, confirmedAt);
    }

    @Override
    @Transactional
    public void onReceiptRevised(UUID petId, UUID logicalReceiptId, long newAmountSats, Instant when) {
        Objects.requireNonNull(logicalReceiptId, "logicalReceiptId");
        Objects.requireNonNull(when, "when");
        Pet pet = loadPet(petId);
        if (newAmountSats == 0L) {
            invalidateExisting(pet, logicalReceiptId, when);
            return;
        }
        Optional<ResolvedPortion> portion = portionPort.currentPositivePortion(petId);
        if (portion.isEmpty()) {
            return;
        }
        Optional<PetFeeding> existing = PetFeeding.findByPetAndReceipt(pet, logicalReceiptId);
        if (existing.isEmpty()) {
            applyNewReceipt(pet, logicalReceiptId, newAmountSats, false, when, portion.get());
            return;
        }
        reviseExisting(pet, existing.get(), portion.get(), newAmountSats, when);
    }

    @Override
    @Transactional
    public void onReceiptInvalidated(UUID petId, UUID logicalReceiptId, Instant when) {
        Objects.requireNonNull(logicalReceiptId, "logicalReceiptId");
        Objects.requireNonNull(when, "when");
        invalidateExisting(loadPet(petId), logicalReceiptId, when);
    }

    @Override
    @Transactional
    public void onBalanceKnown(UUID petId, long confirmedSats, long pendingIncomingSats, Instant when) {
        Objects.requireNonNull(when, "when");
        applyKnownBalance(loadPet(petId), confirmedSats, when);
    }

    @Override
    @Transactional
    public void onProviderFailure(UUID petId, Instant when) {
        loadPet(petId);
        LOG.infof("onProviderFailure no-op petId=%s when=%s", petId, when);
    }

    @Override
    @Transactional
    public void tick(UUID petId, Instant now) {
        Objects.requireNonNull(now, "now");
        Pet pet = loadPet(petId);
        evaluate(pet, now);
        if (pet.presentation == PetPresentation.CREATURE
                && pet.bornAt != null
                && EggPolicy.graceElapsed(pet.zeroBalanceSince, now)) {
            returnToEgg(pet, now);
        }
    }

    @Override
    @Transactional
    public void reconstruct(UUID petId, Instant now) {
        Objects.requireNonNull(now, "now");
        Pet pet = loadPet(petId);
        Optional<ResolvedPortion> portion = portionPort.currentPositivePortion(petId);
        if (portion.isEmpty()) {
            return;
        }
        long portionSats = portion.get().portionSats();
        List<ReplayEvent> events = orderConfirmedReceipts(pet);
        pet.reserveHours = ZERO_HOURS;
        pet.reserveDepletedAt = null;
        pet.lastEvaluatedAt = events.isEmpty() ? pet.createdAt : events.getFirst().effectiveAt();
        for (ReplayEvent event : events) {
            replay(pet, event, portionSats);
        }
        long[] totals = sumAddressSats(pet.address);
        applyKnownBalance(pet, totals[0], now);
    }

    private static void replay(Pet pet, ReplayEvent event, long portionSats) {
        LogicalReceipt receipt = event.receipt();
        Instant effectiveAt = event.effectiveAt();
        evaluate(pet, effectiveAt);
        Optional<PetFeeding> existing = PetFeeding.findByPetAndReceipt(pet, receipt.id);
        if (existing.isEmpty()) {
            BigDecimal duration = ReserveMath.hoursAdded(receipt.confirmedSats, portionSats);
            creditDelta(pet, duration, effectiveAt);
            PetFeeding.create(
                    pet,
                    receipt.id,
                    receipt.confirmedSats,
                    portionSats,
                    duration,
                    effectiveAt,
                    FeedingStatus.VALID,
                    FeedingOrigin.HISTORICAL_RECONSTRUCTION,
                    false,
                    effectiveAt
            ).persist();
            return;
        }
        PetFeeding feeding = existing.get();
        if (feeding.status == FeedingStatus.INVALIDATED) {
            return;
        }
        if (feeding.status != FeedingStatus.VALID) {
            return;
        }
        creditDelta(pet, feeding.durationHours, effectiveAt);
        if (effectiveAt.isBefore(pet.createdAt) || feeding.origin == FeedingOrigin.HISTORICAL_RECONSTRUCTION) {
            feeding.presentable = false;
        }
        feeding.updatedAt = effectiveAt;
    }

    private static List<ReplayEvent> orderConfirmedReceipts(Pet pet) {
        Map<String, BitcoinTransaction> byTxid = BitcoinTransaction.list("address", pet.address)
                .stream()
                .collect(Collectors.toMap(
                        tx -> tx.txid,
                        Function.identity(),
                        (first, ignored) -> first));
        return LogicalReceipt.findByAddress(pet.address).stream()
                .filter(receipt -> receipt.confirmedSats > 0L)
                .map(receipt -> {
                    BitcoinTransaction tx = byTxid.get(receipt.referenceTxid);
                    return new ReplayEvent(receipt, tx, effectiveAt(tx, pet.createdAt));
                })
                .sorted(REPLAY_ORDER)
                .toList();
    }

    private static Instant effectiveAt(BitcoinTransaction tx, Instant petCreatedAt) {
        if (tx == null) {
            return petCreatedAt;
        }
        if (tx.confirmedAt != null) {
            return tx.confirmedAt;
        }
        if (tx.observedAt != null) {
            return tx.observedAt;
        }
        return petCreatedAt;
    }

    private static long[] sumAddressSats(Address address) {
        long confirmed = 0L;
        long pending = 0L;
        for (LogicalReceipt receipt : LogicalReceipt.findByAddress(address)) {
            confirmed += receipt.confirmedSats;
            pending += receipt.pendingSats;
        }
        return new long[] {confirmed, pending};
    }

    /**
     * pendingIncomingSats não nasce, não reaparece e não limpa a carência (CA-012 / CC-12).
     */
    private static void applyKnownBalance(Pet pet, long confirmedSats, Instant when) {
        evaluate(pet, when);
        pet.zeroBalanceSince = EggPolicy.nextZeroBalanceSince(pet.zeroBalanceSince, confirmedSats, when);
        if (EggPolicy.canAppear(confirmedSats)) {
            appear(pet, when);
        } else if (pet.presentation == PetPresentation.CREATURE
                && pet.bornAt != null
                && EggPolicy.graceElapsed(pet.zeroBalanceSince, when)) {
            returnToEgg(pet, when);
        }
    }

    private record ReplayEvent(LogicalReceipt receipt, BitcoinTransaction tx, Instant effectiveAt) {
    }

    private static final Comparator<ReplayEvent> REPLAY_ORDER = Comparator
            .comparing((ReplayEvent event) -> event.tx() == null ? null : event.tx().blockHeight,
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ReplayEvent::effectiveAt)
            .thenComparing(event -> event.receipt().referenceTxid);

    private void handleExistingObservation(
            Pet pet,
            PetFeeding feeding,
            ResolvedPortion portion,
            long amountSats,
            boolean confirmed,
            Instant when
    ) {
        if (feeding.status == FeedingStatus.INVALIDATED) {
            return;
        }
        if (feeding.amountSats != amountSats) {
            reviseExisting(pet, feeding, portion, amountSats, when);
            return;
        }
        if (confirmed && feeding.status == FeedingStatus.PROVISIONAL) {
            confirmExisting(pet, feeding, portion, amountSats, when);
            return;
        }
        if (!confirmed && feeding.status == FeedingStatus.VALID) {
            demoteValidOnReorg(pet, feeding, when);
        }
    }

    private void applyNewReceipt(
            Pet pet,
            UUID logicalReceiptId,
            long amountSats,
            boolean confirmed,
            Instant when,
            ResolvedPortion portion
    ) {
        evaluate(pet, when);
        FeedingStatus status = confirmed ? FeedingStatus.VALID : FeedingStatus.PROVISIONAL;
        BigDecimal duration = durationFor(pet, status, amountSats, portion.portionSats());
        creditDelta(pet, duration, when);
        boolean presentable = isPresentable(pet, confirmed);
        PetFeeding.create(
                pet,
                logicalReceiptId,
                amountSats,
                portion.portionSats(),
                duration,
                when,
                status,
                FeedingOrigin.LIVE,
                presentable,
                when
        ).persist();
    }

    private void confirmExisting(
            Pet pet,
            PetFeeding feeding,
            ResolvedPortion portion,
            long amountSats,
            Instant when
    ) {
        if (feeding.status == FeedingStatus.VALID || feeding.status == FeedingStatus.INVALIDATED) {
            return;
        }
        if (pet.presentation == PetPresentation.CREATURE) {
            feeding.status = FeedingStatus.VALID;
            feeding.presentable = true;
            feeding.updatedAt = when;
            return;
        }
        evaluate(pet, when);
        BigDecimal oldCredited = creditedHours(pet, feeding);
        feeding.amountSats = amountSats;
        feeding.status = FeedingStatus.VALID;
        feeding.presentable = true;
        feeding.portionSats = portion.portionSats();
        feeding.durationHours = durationFor(pet, FeedingStatus.VALID, amountSats, portion.portionSats());
        feeding.updatedAt = when;
        creditDelta(pet, feeding.durationHours.subtract(oldCredited), when);
    }

    private void reviseExisting(
            Pet pet,
            PetFeeding feeding,
            ResolvedPortion portion,
            long newAmountSats,
            Instant when
    ) {
        if (feeding.status == FeedingStatus.INVALIDATED) {
            return;
        }
        evaluate(pet, when);
        BigDecimal oldCredited = creditedHours(pet, feeding);
        feeding.amountSats = newAmountSats;
        feeding.portionSats = portion.portionSats();
        feeding.durationHours = durationFor(pet, feeding.status, newAmountSats, portion.portionSats());
        feeding.updatedAt = when;
        creditDelta(pet, feeding.durationHours.subtract(oldCredited), when);
    }

    private void invalidateExisting(Pet pet, UUID logicalReceiptId, Instant when) {
        Optional<PetFeeding> existing = PetFeeding.findByPetAndReceipt(pet, logicalReceiptId);
        if (existing.isEmpty() || existing.get().status == FeedingStatus.INVALIDATED) {
            return;
        }
        PetFeeding feeding = existing.get();
        evaluate(pet, when);
        creditDelta(pet, creditedHours(pet, feeding).negate(), when);
        feeding.status = FeedingStatus.INVALIDATED;
        feeding.updatedAt = when;
        if (EggPolicy.immediateEggOnLostBirthFoundation(
                pet.bornAt != null,
                hasOtherValidFeeding(pet, logicalReceiptId),
                0L)) {
            returnToEgg(pet, when);
        }
    }

    private void demoteValidOnReorg(Pet pet, PetFeeding feeding, Instant when) {
        evaluate(pet, when);
        if (pet.presentation == PetPresentation.EGG) {
            creditDelta(pet, feeding.durationHours.negate(), when);
            feeding.durationHours = ZERO_HOURS;
            feeding.status = FeedingStatus.PROVISIONAL;
            feeding.presentable = false;
        } else {
            feeding.status = FeedingStatus.PROVISIONAL;
        }
        feeding.updatedAt = when;
        if (EggPolicy.immediateEggOnLostBirthFoundation(
                pet.bornAt != null,
                hasOtherValidFeeding(pet, feeding.logicalReceiptId),
                0L)) {
            returnToEgg(pet, when);
        }
    }

    private static void appear(Pet pet, Instant when) {
        if (pet.bornAt == null) {
            pet.bornAt = when;
            if (pet.artworkStatus == ArtworkStatus.NONE) {
                pet.artworkStatus = ArtworkStatus.PENDING;
            }
            if (pet.artworkStatus == ArtworkStatus.APPROVED) {
                pet.presentation = PetPresentation.CREATURE;
            }
            pet.updatedAt = when;
            return;
        }
        if (pet.presentation == PetPresentation.EGG) {
            pet.lastReappearedAt = when;
            if (pet.artworkStatus == ArtworkStatus.APPROVED) {
                pet.presentation = PetPresentation.CREATURE;
            }
            pet.updatedAt = when;
            return;
        }
        pet.updatedAt = when;
    }

    private static void returnToEgg(Pet pet, Instant when) {
        pet.presentation = PetPresentation.EGG;
        pet.lastReturnedToEggAt = when;
        pet.updatedAt = when;
    }

    private static boolean hasOtherValidFeeding(Pet pet, UUID logicalReceiptId) {
        return PetFeeding.count(
                "pet = ?1 AND status = ?2 AND logicalReceiptId <> ?3",
                pet,
                FeedingStatus.VALID,
                logicalReceiptId
        ) > 0;
    }

    private static void evaluate(Pet pet, Instant now) {
        ReserveClock.Consumption consumed = ReserveClock.consume(
                pet.reserveHours, pet.lastEvaluatedAt, pet.reserveDepletedAt, now);
        pet.reserveHours = consumed.remainingHours();
        pet.reserveDepletedAt = consumed.depletedAt();
        pet.lastEvaluatedAt = consumed.evaluatedAt();
        pet.emotionalState = EmotionalStatePolicy.of(pet.reserveHours, pet.reserveDepletedAt, now);
        pet.updatedAt = now;
    }

    private static void creditDelta(Pet pet, BigDecimal delta, Instant now) {
        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        pet.reserveHours = ReserveMath.applyCap(pet.reserveHours, delta);
        if (pet.reserveHours.compareTo(BigDecimal.ZERO) > 0) {
            pet.reserveDepletedAt = null;
        } else if (pet.reserveDepletedAt == null) {
            pet.reserveDepletedAt = now;
        }
        pet.emotionalState = EmotionalStatePolicy.of(pet.reserveHours, pet.reserveDepletedAt, now);
        pet.updatedAt = now;
    }

    private static BigDecimal durationFor(Pet pet, FeedingStatus status, long amountSats, long portionSats) {
        if (status == FeedingStatus.INVALIDATED) {
            return ZERO_HOURS;
        }
        if (pet.presentation == PetPresentation.EGG && status == FeedingStatus.PROVISIONAL) {
            return ZERO_HOURS;
        }
        return ReserveMath.hoursAdded(amountSats, portionSats);
    }

    private static BigDecimal creditedHours(Pet pet, PetFeeding feeding) {
        if (feeding.status == FeedingStatus.INVALIDATED) {
            return ZERO_HOURS;
        }
        if (pet.presentation == PetPresentation.EGG && feeding.status == FeedingStatus.PROVISIONAL) {
            return ZERO_HOURS;
        }
        return feeding.durationHours;
    }

    private static boolean isPresentable(Pet pet, boolean confirmed) {
        return pet.presentation != PetPresentation.EGG || confirmed;
    }

    private static Pet loadPet(UUID petId) {
        Objects.requireNonNull(petId, "petId");
        Pet pet = Pet.findById(petId);
        if (pet == null) {
            throw new IllegalArgumentException("Pet não encontrado: " + petId);
        }
        return pet;
    }
}
