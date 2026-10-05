package br.com.satoshipet.api.pet.engine;

import br.com.satoshipet.api.btc.BitcoinTransaction;
import br.com.satoshipet.api.btc.AddressMonitorState;
import br.com.satoshipet.api.btc.LogicalReceipt;
import br.com.satoshipet.api.outbox.OutboxService;
import br.com.satoshipet.api.pet.ArtworkStatus;
import br.com.satoshipet.api.pet.FeedingOrigin;
import br.com.satoshipet.api.pet.FeedingStatus;
import br.com.satoshipet.api.pet.Pet;
import br.com.satoshipet.api.pet.PetFeeding;
import br.com.satoshipet.api.pet.PetFeedingEventIds;
import br.com.satoshipet.api.pet.PetLifecyclePort;
import br.com.satoshipet.api.pet.PetPresentation;
import br.com.satoshipet.api.pet.PetReferencePortionPort;
import br.com.satoshipet.api.pet.PetReferencePortionPort.ResolvedPortion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
    private static final String AGGREGATE_PET = "Pet";
    static final String PET_FEEDING_APPLIED = "PET_FEEDING_APPLIED";
    static final String PET_FEEDING_REVISED = "PET_FEEDING_REVISED";
    static final String PET_FEEDING_INVALIDATED = "PET_FEEDING_INVALIDATED";
    static final String PET_BORN = "PET_BORN";
    static final String PET_RETURNED_TO_EGG = "PET_RETURNED_TO_EGG";
    static final String PET_REAPPEARED = "PET_REAPPEARED";
    static final String PET_STATE_CHANGED = "PET_STATE_CHANGED";

    private final PetReferencePortionPort portionPort;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Inject
    public PetEngine(
            PetReferencePortionPort portionPort,
            OutboxService outboxService,
            ObjectMapper objectMapper
    ) {
        this.portionPort = portionPort;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
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
        PetSnapshot before = snapshot(pet);
        Optional<PetFeeding> existing = PetFeeding.findByPetAndReceipt(pet, logicalReceiptId);
        if (existing.isPresent()) {
            handleExistingObservation(pet, existing.get(), portion.get(), amountSats, confirmed, observedAt);
            emitLifecycle(pet, observedAt, before, true);
            return;
        }
        applyNewReceipt(pet, logicalReceiptId, amountSats, confirmed, observedAt, portion.get());
        emitLifecycle(pet, observedAt, before, true);
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
        PetSnapshot before = snapshot(pet);
        Optional<PetFeeding> existing = PetFeeding.findByPetAndReceipt(pet, logicalReceiptId);
        if (existing.isEmpty()) {
            applyNewReceipt(pet, logicalReceiptId, amountSats, true, confirmedAt, portion.get());
            emitLifecycle(pet, confirmedAt, before, true);
            return;
        }
        handleExistingObservation(pet, existing.get(), portion.get(), amountSats, true, confirmedAt);
        emitLifecycle(pet, confirmedAt, before, true);
    }

    @Override
    @Transactional
    public void onReceiptRevised(UUID petId, UUID logicalReceiptId, long newAmountSats, Instant when) {
        Objects.requireNonNull(logicalReceiptId, "logicalReceiptId");
        Objects.requireNonNull(when, "when");
        Pet pet = loadPet(petId);
        PetSnapshot before = snapshot(pet);
        if (newAmountSats == 0L) {
            invalidateExisting(pet, logicalReceiptId, when);
            emitLifecycle(pet, when, before, true);
            return;
        }
        Optional<ResolvedPortion> portion = portionPort.currentPositivePortion(petId);
        if (portion.isEmpty()) {
            return;
        }
        Optional<PetFeeding> existing = PetFeeding.findByPetAndReceipt(pet, logicalReceiptId);
        if (existing.isEmpty()) {
            applyNewReceipt(pet, logicalReceiptId, newAmountSats, false, when, portion.get());
            emitLifecycle(pet, when, before, true);
            return;
        }
        reviseExisting(pet, existing.get(), portion.get(), newAmountSats, when);
        emitLifecycle(pet, when, before, true);
    }

    @Override
    @Transactional
    public void onReceiptInvalidated(UUID petId, UUID logicalReceiptId, Instant when) {
        Objects.requireNonNull(logicalReceiptId, "logicalReceiptId");
        Objects.requireNonNull(when, "when");
        Pet pet = loadPet(petId);
        PetSnapshot before = snapshot(pet);
        invalidateExisting(pet, logicalReceiptId, when);
        emitLifecycle(pet, when, before, true);
    }

    @Override
    @Transactional
    public void onBalanceKnown(UUID petId, long confirmedSats, long pendingIncomingSats, Instant when) {
        Objects.requireNonNull(when, "when");
        Pet pet = loadPet(petId);
        AddressMonitorState state = AddressMonitorState.loadOrCreate(pet.address, when);
        if (state.balanceCheckedAt != null && when.isBefore(state.balanceCheckedAt)) return;
        state.recordBalance(confirmedSats, pendingIncomingSats, when);
        PetSnapshot before = snapshot(pet);
        applyKnownBalance(pet, confirmedSats, when);
        emitLifecycle(pet, when, before, true);
    }

    @Override
    @Transactional
    public void onProviderFailure(UUID petId, Instant when) {
        Pet pet = loadPet(petId);
        AddressMonitorState state = AddressMonitorState.loadOrCreate(pet.address, when);
        if (state.balanceCheckedAt == null || !when.isBefore(state.balanceCheckedAt)) {
            state.providerAvailable = false;
        }
    }

    @Override
    @Transactional
    public void onArtworkApproved(UUID petId, Instant now) {
        Pet pet = loadPet(petId);
        PetSnapshot before = snapshot(pet);
        AddressMonitorState.findByAddress(pet.address)
                .filter(state -> state.isFresh(now) && state.confirmedBalanceSats != null)
                .ifPresent(state -> applyKnownBalance(pet, state.confirmedBalanceSats, now));
        emitLifecycle(pet, now, before, true);
    }

    @Override
    @Transactional
    public void tick(UUID petId, Instant now) {
        Objects.requireNonNull(now, "now");
        Pet pet = loadPet(petId);
        PetSnapshot before = snapshot(pet);
        evaluate(pet, now);
        if (pet.presentation == PetPresentation.CREATURE
                && pet.bornAt != null
                && EggPolicy.graceElapsed(pet.zeroBalanceSince, now)
                && AddressMonitorState.findByAddress(pet.address)
                    .filter(state -> state.providerAvailable && state.confirmedBalanceSats != null
                            && state.confirmedBalanceSats == 0L
                            && state.balanceCheckedAt != null && !state.balanceCheckedAt.isBefore(now))
                    .isPresent()) {
            returnToEgg(pet, now);
        }
        emitLifecycle(pet, now, before, true);
    }

    @Override
    @Transactional
    public void reconstruct(UUID petId, Instant now) {
        Pet pet = loadPet(petId);
        reconstruct(pet, now, pet.awaitingReference);
    }

    @Override
    @Transactional
    public void reconstruct(UUID petId, Instant now, boolean awaitingReferenceBefore) {
        reconstruct(loadPet(petId), now, awaitingReferenceBefore);
    }

    private void reconstruct(Pet pet, Instant now, boolean awaitingReferenceBefore) {
        Objects.requireNonNull(now, "now");
        Optional<ResolvedPortion> portion = portionPort.currentPositivePortion(pet.id);
        if (portion.isEmpty()) {
            return;
        }
        PetSnapshot live = snapshot(pet);
        PetSnapshot before = new PetSnapshot(
                live.presentation(),
                live.emotionalState(),
                live.bornAt(),
                live.lastReappearedAt(),
                awaitingReferenceBefore
        );
        Instant projectionTime = now.isBefore(pet.lastEvaluatedAt) ? pet.lastEvaluatedAt : now;
        long portionSats = portion.get().portionSats();
        List<ReplayEvent> events = orderConfirmedReceipts(pet);
        pet.reserveHours = ZERO_HOURS;
        pet.reserveDepletedAt = null;
        pet.lastEvaluatedAt = events.isEmpty() ? pet.createdAt : events.getFirst().effectiveAt();
        for (ReplayEvent event : events) {
            replay(pet, event, portionSats);
        }
        replayEligibleFeedings(pet, projectionTime);
        AddressMonitorState.findByAddress(pet.address)
                .filter(state -> state.isFresh(projectionTime) && state.confirmedBalanceSats != null)
                .ifPresent(state -> applyKnownBalance(pet, state.confirmedBalanceSats, projectionTime));
        emitLifecycle(pet, projectionTime, before, false);
    }

    private static void replay(Pet pet, ReplayEvent event, long portionSats) {
        LogicalReceipt receipt = event.receipt();
        Instant effectiveAt = event.effectiveAt();
        evaluate(pet, effectiveAt);
        Optional<PetFeeding> existing = PetFeeding.findByPetAndReceipt(pet, receipt.id);
        if (existing.isEmpty()) {
            BigDecimal theoretical = ReserveMath.hoursAdded(receipt.confirmedSats, portionSats);
            BigDecimal applied = creditDelta(pet, theoretical, effectiveAt);
            PetFeeding.create(
                    pet,
                    receipt.id,
                    receipt.confirmedSats,
                    portionSats,
                    applied,
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
        creditDelta(pet, ReserveMath.hoursAdded(feeding.amountSats, feeding.portionSats), feeding.creditEffectiveAt);
        if (effectiveAt.isBefore(pet.createdAt) || feeding.origin == FeedingOrigin.HISTORICAL_RECONSTRUCTION) {
            feeding.presentable = false;
        }
        feeding.updatedAt = effectiveAt;
    }

    private static List<ReplayEvent> orderConfirmedReceipts(Pet pet) {
        List<BitcoinTransaction> transactions = BitcoinTransaction.list("address", pet.address);
        Map<String, BitcoinTransaction> byTxid = new HashMap<>();
        for (BitcoinTransaction tx : transactions) {
            byTxid.putIfAbsent(tx.txid, tx);
        }
        return LogicalReceipt.findByAddress(pet.address).stream()
                .filter(receipt -> receipt.confirmedSats > 0L)
                .map(receipt -> {
                    BitcoinTransaction tx = BitcoinTransaction.findCurrentByReceipt(receipt)
                            .orElse(byTxid.get(receipt.referenceTxid));
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

    /**
     * pendingIncomingSats não nasce, não reaparece e não limpa a carência (CA-012 / CC-12).
     */
    private static void applyKnownBalance(Pet pet, long confirmedSats, Instant when) {
        evaluate(pet, when);
        pet.zeroBalanceSince = EggPolicy.nextZeroBalanceSince(pet.zeroBalanceSince, confirmedSats, when);
        if (EggPolicy.canAppear(confirmedSats)) {
            pet.birthFoundationLost = false;
            appear(pet, when);
        } else if (pet.presentation == PetPresentation.CREATURE
                && pet.bornAt != null
                && (pet.birthFoundationLost || EggPolicy.graceElapsed(pet.zeroBalanceSince, when))) {
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
        if (amountSats <= 0L) {
            invalidateExisting(pet, feeding.logicalReceiptId, when);
            return;
        }
        if (feeding.status == FeedingStatus.INVALIDATED) {
            feeding.status = confirmed ? FeedingStatus.VALID : FeedingStatus.PROVISIONAL;
            feeding.amountSats = amountSats;
            feeding.presentable = isPresentable(pet, confirmed);
            if (!feeding.reserveEligible && (confirmed || feeding.presentable)) {
                feeding.creditEffectiveAt = when;
                feeding.portionSats = portion.portionSats();
            }
            feeding.reserveEligible = feeding.reserveEligible || confirmed || feeding.presentable;
            feeding.durationHours = feeding.reserveEligible
                    ? ReserveMath.hoursAdded(amountSats, feeding.portionSats) : ZERO_HOURS;
            feeding.updatedAt = when;
            replayEligibleFeedings(pet, when);
            emitFeeding(PET_FEEDING_REVISED, pet, feeding, when);
            return;
        }
        if (feeding.amountSats != amountSats) {
            reviseExisting(pet, feeding, portion, amountSats, when);
        }
        if (confirmed && feeding.status == FeedingStatus.PROVISIONAL) {
            confirmExisting(pet, feeding, portion, amountSats, when);
        } else if (!confirmed && feeding.status == FeedingStatus.VALID) {
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
        Instant projectionTime = when.isBefore(pet.lastEvaluatedAt) ? pet.lastEvaluatedAt : when;
        boolean historical = when.isBefore(pet.createdAt);
        evaluate(pet, when);
        FeedingStatus status = confirmed ? FeedingStatus.VALID : FeedingStatus.PROVISIONAL;
        BigDecimal theoretical = durationFor(pet, status, amountSats, portion.portionSats());
        BigDecimal applied = creditDelta(pet, theoretical, when);
        boolean presentable = !historical && isPresentable(pet, confirmed);
        PetFeeding feeding = PetFeeding.create(
                pet,
                logicalReceiptId,
                amountSats,
                portion.portionSats(),
                applied,
                when,
                status,
                historical ? FeedingOrigin.HISTORICAL_RECONSTRUCTION : FeedingOrigin.LIVE,
                presentable,
                when
        );
        feeding.persist();
        if (when.isBefore(projectionTime)) {
            replayEligibleFeedings(pet, projectionTime);
        }
        if (presentable) {
            emitFeeding(PET_FEEDING_APPLIED, pet, feeding, when);
        }
    }

    private void confirmExisting(
            Pet pet,
            PetFeeding feeding,
            ResolvedPortion portion,
            long amountSats,
            Instant when
    ) {
        if (feeding.status == FeedingStatus.INVALIDATED) {
            handleExistingObservation(pet, feeding, portion, amountSats, true, when);
            return;
        }
        if (feeding.status == FeedingStatus.VALID) return;
        long oldAmount = feeding.amountSats;
        BigDecimal oldDuration = feeding.durationHours;
        FeedingStatus oldStatus = feeding.status;
        boolean wasPresentable = feeding.presentable;
        evaluate(pet, when);
        feeding.amountSats = amountSats;
        feeding.status = FeedingStatus.VALID;
        feeding.presentable = true;
        if (!feeding.reserveEligible) {
            feeding.creditEffectiveAt = when;
            feeding.portionSats = portion.portionSats();
            feeding.reserveEligible = true;
            feeding.durationHours = creditDelta(pet,
                    ReserveMath.hoursAdded(amountSats, feeding.portionSats), when);
        } else if (oldAmount != amountSats) {
            feeding.durationHours = ReserveMath.hoursAdded(amountSats, feeding.portionSats);
            replayEligibleFeedings(pet, when);
        }
        feeding.updatedAt = when;
        maybeEmitAppliedOrRevised(pet, feeding, when, oldAmount, oldDuration, oldStatus, wasPresentable);
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
        long oldAmount = feeding.amountSats;
        BigDecimal oldDuration = feeding.durationHours;
        FeedingStatus oldStatus = feeding.status;
        boolean wasPresentable = feeding.presentable;
        evaluate(pet, when);
        feeding.amountSats = newAmountSats;
        feeding.durationHours = feeding.reserveEligible
                ? ReserveMath.hoursAdded(newAmountSats, feeding.portionSats) : ZERO_HOURS;
        feeding.updatedAt = when;
        replayEligibleFeedings(pet, when);
        maybeEmitAppliedOrRevised(pet, feeding, when, oldAmount, oldDuration, oldStatus, wasPresentable);
    }

    private void invalidateExisting(Pet pet, UUID logicalReceiptId, Instant when) {
        Optional<PetFeeding> existing = PetFeeding.findByPetAndReceipt(pet, logicalReceiptId);
        if (existing.isEmpty() || existing.get().status == FeedingStatus.INVALIDATED) {
            return;
        }
        PetFeeding feeding = existing.get();
        boolean wasPresentable = feeding.presentable;
        evaluate(pet, when);
        feeding.status = FeedingStatus.INVALIDATED;
        feeding.presentable = false;
        feeding.updatedAt = when;
        replayEligibleFeedings(pet, when);
        reconcileLostBirthFoundation(pet, logicalReceiptId, when);
        if (feeding.origin == FeedingOrigin.LIVE && wasPresentable) {
            emitFeeding(PET_FEEDING_INVALIDATED, pet, feeding, when);
        }
    }

    private void demoteValidOnReorg(Pet pet, PetFeeding feeding, Instant when) {
        long oldAmount = feeding.amountSats;
        BigDecimal oldDuration = feeding.durationHours;
        FeedingStatus oldStatus = feeding.status;
        boolean wasPresentable = feeding.presentable;
        evaluate(pet, when);
        if (pet.presentation == PetPresentation.EGG) {
            feeding.reserveEligible = false;
            feeding.durationHours = ZERO_HOURS;
            feeding.status = FeedingStatus.PROVISIONAL;
            feeding.presentable = false;
        } else {
            feeding.status = FeedingStatus.PROVISIONAL;
        }
        feeding.updatedAt = when;
        replayEligibleFeedings(pet, when);
        reconcileLostBirthFoundation(pet, feeding.logicalReceiptId, when);
        maybeEmitAppliedOrRevised(pet, feeding, when, oldAmount, oldDuration, oldStatus, wasPresentable);
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

    private static void reconcileLostBirthFoundation(Pet pet, UUID receiptId, Instant when) {
        if (pet.bornAt == null || hasOtherValidFeeding(pet, receiptId)) return;
        pet.birthFoundationLost = true;
        AddressMonitorState.findByAddress(pet.address)
                .filter(state -> state.isFresh(when) && state.confirmedBalanceSats != null)
                .ifPresent(state -> applyKnownBalance(pet, state.confirmedBalanceSats, when));
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
        pet.emotionalState = EmotionalStatePolicy.of(pet.reserveHours, pet.reserveDepletedAt, consumed.evaluatedAt());
        pet.updatedAt = consumed.evaluatedAt().isBefore(pet.updatedAt) ? pet.updatedAt : consumed.evaluatedAt();
    }

    /** Reprojeta a reserva sem reescrever o crédito originalmente aplicado sob o teto. */
    private static void replayEligibleFeedings(Pet pet, Instant now) {
        List<PetFeeding> feedings = PetFeeding.listByPet(pet).stream()
                .filter(f -> f.status != FeedingStatus.INVALIDATED)
                .filter(f -> f.reserveEligible)
                .sorted(Comparator.comparing((PetFeeding f) -> f.creditEffectiveAt).thenComparing(f -> f.id))
                .toList();
        Instant until = now.isBefore(pet.lastEvaluatedAt) ? pet.lastEvaluatedAt : now;
        pet.reserveHours = ZERO_HOURS;
        pet.reserveDepletedAt = null;
        pet.lastEvaluatedAt = feedings.isEmpty() ? pet.createdAt : feedings.getFirst().creditEffectiveAt;
        for (PetFeeding feeding : feedings) {
            evaluate(pet, feeding.creditEffectiveAt);
            creditDelta(pet, ReserveMath.hoursAdded(feeding.amountSats, feeding.portionSats), feeding.creditEffectiveAt);
        }
        evaluate(pet, until);
    }

    private static BigDecimal creditDelta(Pet pet, BigDecimal delta, Instant now) {
        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            return ZERO_HOURS;
        }
        BigDecimal before = pet.reserveHours;
        pet.reserveHours = ReserveMath.applyCap(pet.reserveHours, delta);
        BigDecimal applied = pet.reserveHours.subtract(before).setScale(ReserveMath.SCALE, RoundingMode.DOWN);
        if (pet.reserveHours.compareTo(BigDecimal.ZERO) > 0) {
            pet.reserveDepletedAt = null;
        } else if (pet.reserveDepletedAt == null) {
            pet.reserveDepletedAt = now;
        }
        pet.emotionalState = EmotionalStatePolicy.of(pet.reserveHours, pet.reserveDepletedAt, now);
        pet.updatedAt = now.isBefore(pet.updatedAt) ? pet.updatedAt : now;
        return applied;
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

    private static boolean isPresentable(Pet pet, boolean confirmed) {
        return pet.presentation != PetPresentation.EGG || confirmed;
    }

    private static PetSnapshot snapshot(Pet pet) {
        return new PetSnapshot(
                pet.presentation,
                pet.emotionalState,
                pet.bornAt,
                pet.lastReappearedAt,
                pet.awaitingReference
        );
    }

    private void emitLifecycle(Pet pet, Instant when, PetSnapshot before, boolean includeEmotionalState) {
        boolean born = before.bornAt() == null && pet.bornAt != null;
        boolean returnedToEgg = before.presentation() == PetPresentation.CREATURE
                && pet.presentation == PetPresentation.EGG;
        boolean reappeared = before.presentation() == PetPresentation.EGG
                && pet.presentation == PetPresentation.CREATURE
                && pet.lastReappearedAt != null
                && (before.lastReappearedAt() == null
                || before.lastReappearedAt().isBefore(pet.lastReappearedAt));
        if (born) {
            emitPetEvent(PET_BORN, pet, when, lifecycleEventId(PET_BORN, pet, when), null);
        }
        if (returnedToEgg) {
            emitPetEvent(
                    PET_RETURNED_TO_EGG,
                    pet,
                    when,
                    lifecycleEventId(PET_RETURNED_TO_EGG, pet, when),
                    null
            );
        }
        if (reappeared) {
            emitPetEvent(
                    PET_REAPPEARED,
                    pet,
                    when,
                    lifecycleEventId(PET_REAPPEARED, pet, when),
                    null
            );
        }
        boolean covered = born || returnedToEgg || reappeared;
        boolean emotionChanged = includeEmotionalState
                && pet.presentation == PetPresentation.CREATURE
                && before.emotionalState() != pet.emotionalState;
        boolean uncoveredFlip = !covered
                && (before.presentation() != pet.presentation
                || before.awaitingReference() != pet.awaitingReference);
        if (emotionChanged || uncoveredFlip) {
            emitPetEvent(
                    PET_STATE_CHANGED,
                    pet,
                    when,
                    lifecycleEventId(PET_STATE_CHANGED, pet, when),
                    null
            );
        }
    }

    private void maybeEmitAppliedOrRevised(
            Pet pet,
            PetFeeding feeding,
            Instant when,
            long oldAmount,
            BigDecimal oldDuration,
            FeedingStatus oldStatus,
            boolean wasPresentable
    ) {
        if (feeding.origin != FeedingOrigin.LIVE) {
            return;
        }
        if (!wasPresentable && feeding.presentable) {
            emitFeeding(PET_FEEDING_APPLIED, pet, feeding, when);
            return;
        }
        maybeEmitRevised(pet, feeding, when, oldAmount, oldDuration, oldStatus, wasPresentable);
    }

    private void maybeEmitRevised(
            Pet pet,
            PetFeeding feeding,
            Instant when,
            long oldAmount,
            BigDecimal oldDuration,
            FeedingStatus oldStatus,
            boolean wasPresentable
    ) {
        if (feeding.origin != FeedingOrigin.LIVE) {
            return;
        }
        if (!feeding.presentable && !wasPresentable) {
            return;
        }
        boolean amountChanged = feeding.amountSats != oldAmount;
        boolean durationChanged = feeding.durationHours.compareTo(oldDuration) != 0;
        boolean statusChanged = feeding.status != oldStatus;
        boolean confirmOnlySameHours = oldStatus == FeedingStatus.PROVISIONAL
                && feeding.status == FeedingStatus.VALID
                && !amountChanged
                && !durationChanged;
        if (confirmOnlySameHours) {
            return;
        }
        if (!amountChanged && !durationChanged && !statusChanged) {
            return;
        }
        if (!feeding.presentable) {
            return;
        }
        emitFeeding(PET_FEEDING_REVISED, pet, feeding, when);
    }

    private void emitFeeding(String eventType, Pet pet, PetFeeding feeding, Instant when) {
        if (feeding.origin != FeedingOrigin.LIVE) {
            return;
        }
        emitPetEvent(eventType, pet, when, PetFeedingEventIds.of(eventType, feeding), feeding);
    }

    private void emitPetEvent(String eventType, Pet pet, Instant when, UUID eventId, PetFeeding feeding) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("address", pet.address.canonical);
        payload.put("eventType", eventType);
        payload.put("occurredAt", when.toString());
        payload.put("presentation", pet.presentation.name());
        if (pet.presentation == PetPresentation.CREATURE) {
            payload.put("emotionalState", pet.emotionalState.name());
        }
        payload.put("reserveHours", pet.reserveHours.toPlainString());
        payload.put("awaitingReference", pet.awaitingReference);
        if (feeding != null) {
            payload.put("feedingStatus", feeding.status.name());
            payload.put("amountSats", feeding.amountSats);
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar evento " + eventType, e);
        }
        outboxService.save(eventId, AGGREGATE_PET, pet.id.toString(), eventType, json, null);
    }

    private static UUID lifecycleEventId(String eventType, Pet pet, Instant when) {
        return UUID.nameUUIDFromBytes(
                (eventType + ":" + pet.id + ":" + when.toEpochMilli()).getBytes(StandardCharsets.UTF_8));
    }

    private record PetSnapshot(
            PetPresentation presentation,
            EmotionalState emotionalState,
            Instant bornAt,
            Instant lastReappearedAt,
            boolean awaitingReference
    ) {
    }

    private static Pet loadPet(UUID petId) {
        return Pet.lockForUpdate(petId);
    }
}
