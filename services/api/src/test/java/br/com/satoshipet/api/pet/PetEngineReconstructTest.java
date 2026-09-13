package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.btc.BitcoinTransaction;
import br.com.satoshipet.api.btc.LogicalReceipt;
import br.com.satoshipet.api.pet.engine.ReserveMath;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reconstrução histórica (PET-08, PET-10, CA-014, CA-026, CA-027).
 */
@QuarkusTest
class PetEngineReconstructTest {

    private static final Instant NOW = Instant.parse("2026-09-13T15:00:00Z");
    /** Um nanossegundo antes do cadastro: createdAt posterior, sem consumo (escala 10). */
    private static final Instant BEFORE_CREATED = NOW.minusNanos(1);
    private static final long PORTION_SATS = 20_000L;
    private static final long FIVE_THOUSAND = 5_000L;
    private static final long TEN_THOUSAND = 10_000L;
    private static final BigDecimal SIX_HOURS = hours("6");
    private static final BigDecimal EIGHTEEN_HOURS = hours("18");
    private static final BigDecimal MAX_RESERVE = hours(Integer.toString(ReserveMath.MAX_RESERVE_HOURS));

    @Inject
    PetLifecyclePort lifecycle;

    @Inject
    PetReferencePortionPort portionPort;

    @Test
    @Transactional
    void semPorcaoReconstructNaoCriaAlimentacaoNemAlteraReserva() {
        Fixture fixture = persistPet("ca026-recon", false);

        lifecycle.reconstruct(fixture.pet.id, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        assertTrue(PetFeeding.listByPet(pet).isEmpty());
        assertEquals(0, pet.reserveHours.compareTo(BigDecimal.ZERO.setScale(ReserveMath.SCALE)));
        assertTrue(pet.awaitingReference);
        assertNull(pet.bornAt);
    }

    @Test
    @Transactional
    void primeiraPorcaoReconstroiDoisRecebimentosHistoricosComoNaoApresentaveis() {
        Fixture fixture = persistPet("ca027", false);
        persistConfirmedReceipt(fixture, FIVE_THOUSAND, 100, BEFORE_CREATED);
        persistConfirmedReceipt(fixture, TEN_THOUSAND, 101, BEFORE_CREATED);

        portionPort.recordPositivePortion(
                fixture.pet.id, fixture.account.id, PORTION_SATS, PortionOrigin.CREATOR_PLAN, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        List<PetFeeding> feedings = PetFeeding.listByPet(pet).stream()
                .sorted(Comparator.comparing(f -> f.amountSats))
                .toList();
        assertEquals(2, feedings.size());
        assertHistorical(feedings.get(0), FIVE_THOUSAND, SIX_HOURS);
        assertHistorical(feedings.get(1), TEN_THOUSAND, hours("12"));
        assertEquals(0, pet.reserveHours.compareTo(EIGHTEEN_HOURS));
        assertEquals(NOW, pet.bornAt);
        assertEquals(ArtworkStatus.PENDING, pet.artworkStatus);
        assertEquals(PetPresentation.EGG, pet.presentation);
        assertEquals(PortionOrigin.CREATOR_PLAN, pet.lastPositivePortionOrigin);
        assertFalse(pet.awaitingReference);
    }

    @Test
    @Transactional
    void recebimentoAnteriorAoCadastroNaoEhApresentavelELivePosteriorPermanece() {
        Fixture fixture = persistPet("ca014", true);
        LogicalReceipt liveReceipt = persistConfirmedReceipt(fixture, FIVE_THOUSAND, 200, NOW);
        lifecycle.onReceiptObserved(fixture.pet.id, liveReceipt.id, FIVE_THOUSAND, true, NOW);
        PetFeeding liveBefore = singleFeeding(Pet.findById(fixture.pet.id));
        assertEquals(FeedingOrigin.LIVE, liveBefore.origin);
        assertTrue(liveBefore.presentable);

        persistConfirmedReceipt(fixture, TEN_THOUSAND, 100, BEFORE_CREATED);

        lifecycle.reconstruct(fixture.pet.id, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        List<PetFeeding> feedings = PetFeeding.listByPet(pet).stream()
                .sorted(Comparator.comparing(f -> f.amountSats))
                .toList();
        assertEquals(2, feedings.size());
        PetFeeding live = feedings.get(0);
        assertEquals(FeedingOrigin.LIVE, live.origin);
        assertTrue(live.presentable);
        PetFeeding historical = feedings.get(1);
        assertHistorical(historical, TEN_THOUSAND, hours("12"));
        assertEquals(0, pet.reserveHours.compareTo(EIGHTEEN_HOURS));
        assertEquals(NOW, pet.bornAt);
    }

    @Test
    @Transactional
    void replayRespeitaTetoDe168hEmCadaEvento() {
        Fixture fixture = persistPet("ca016-recon", false);
        persistConfirmedReceipt(fixture, 200_000L, 100, BEFORE_CREATED);
        persistConfirmedReceipt(fixture, 200_000L, 101, BEFORE_CREATED);

        portionPort.recordPositivePortion(
                fixture.pet.id, fixture.account.id, PORTION_SATS, PortionOrigin.CREATOR_PLAN, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(2, PetFeeding.listByPet(pet).size());
        assertEquals(0, pet.reserveHours.compareTo(MAX_RESERVE));
        assertTrue(pet.reserveHours.compareTo(MAX_RESERVE) <= 0);
    }

    @Test
    @Transactional
    void reconstructDuplicadoEIdempotente() {
        Fixture fixture = persistPet("idempotent", false);
        persistConfirmedReceipt(fixture, FIVE_THOUSAND, 100, BEFORE_CREATED);
        persistConfirmedReceipt(fixture, TEN_THOUSAND, 101, BEFORE_CREATED);
        portionPort.recordPositivePortion(
                fixture.pet.id, fixture.account.id, PORTION_SATS, PortionOrigin.CREATOR_PLAN, NOW);

        lifecycle.reconstruct(fixture.pet.id, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(2, PetFeeding.listByPet(pet).size());
        assertEquals(0, pet.reserveHours.compareTo(EIGHTEEN_HOURS));
        assertTrue(PetFeeding.listByPet(pet).stream().noneMatch(feeding -> feeding.presentable));
    }

    @Test
    @Transactional
    void alimentacaoInvalidadaNaoEhCreditadaNoReplay() {
        Fixture fixture = persistPet("invalidated", true);
        LogicalReceipt receipt = persistConfirmedReceipt(fixture, FIVE_THOUSAND, 100, BEFORE_CREATED);
        PetFeeding.create(
                fixture.pet,
                receipt.id,
                FIVE_THOUSAND,
                PORTION_SATS,
                SIX_HOURS,
                BEFORE_CREATED,
                FeedingStatus.INVALIDATED,
                FeedingOrigin.HISTORICAL_RECONSTRUCTION,
                false,
                BEFORE_CREATED
        ).persist();

        lifecycle.reconstruct(fixture.pet.id, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        PetFeeding feeding = singleFeeding(pet);
        assertEquals(FeedingStatus.INVALIDATED, feeding.status);
        assertEquals(0, pet.reserveHours.compareTo(BigDecimal.ZERO.setScale(ReserveMath.SCALE)));
    }

    private static void assertHistorical(PetFeeding feeding, long amountSats, BigDecimal duration) {
        assertEquals(amountSats, feeding.amountSats);
        assertEquals(FeedingStatus.VALID, feeding.status);
        assertEquals(FeedingOrigin.HISTORICAL_RECONSTRUCTION, feeding.origin);
        assertFalse(feeding.presentable);
        assertEquals(0, feeding.durationHours.compareTo(duration));
        assertEquals(PORTION_SATS, feeding.portionSats);
    }

    private Fixture persistPet(String marker, boolean withPortion) {
        Address address = Address.create(uniqueCanonical(marker), NOW);
        address.persist();
        Account account = Account.create(
                marker + "-" + UUID.randomUUID() + "@test.com",
                "America/Sao_Paulo",
                "pt-BR",
                NOW
        );
        account.persist();
        AccountAddressBinding.create(account, address, true, NOW).persist();
        Pet pet = Pet.create(address, account, "Pixel-" + marker, NOW);
        pet.persist();
        if (withPortion) {
            portionPort.recordPositivePortion(
                    pet.id, account.id, PORTION_SATS, PortionOrigin.CREATOR_PLAN, NOW);
        }
        return new Fixture(pet, account, address);
    }

    private static LogicalReceipt persistConfirmedReceipt(
            Fixture fixture, long amountSats, int blockHeight, Instant confirmedAt
    ) {
        String txid = uniqueTxid();
        BitcoinTransaction tx = BitcoinTransaction.createPending(
                txid, fixture.address, amountSats, confirmedAt);
        tx.status = BitcoinTransaction.Status.CONFIRMED;
        tx.confirmedAt = confirmedAt;
        tx.blockHeight = blockHeight;
        tx.blockHash = uniqueTxid();
        tx.persist();
        LogicalReceipt receipt = LogicalReceipt.createPending(
                fixture.address, txid, amountSats, confirmedAt);
        receipt.confirmedSats = amountSats;
        receipt.pendingSats = 0L;
        receipt.persist();
        return receipt;
    }

    private static PetFeeding singleFeeding(Pet pet) {
        List<PetFeeding> feedings = PetFeeding.listByPet(pet);
        assertEquals(1, feedings.size());
        return feedings.get(0);
    }

    private static BigDecimal hours(String value) {
        return new BigDecimal(value).setScale(ReserveMath.SCALE);
    }

    private static String uniqueCanonical(String marker) {
        return "bcrt1q" + marker.replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private static String uniqueTxid() {
        String hex = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        return hex.substring(0, 64);
    }

    private record Fixture(Pet pet, Account account, Address address) {
    }
}
