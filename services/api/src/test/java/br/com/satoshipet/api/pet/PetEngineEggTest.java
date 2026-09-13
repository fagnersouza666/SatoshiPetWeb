package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.btc.LogicalReceipt;
import br.com.satoshipet.api.pet.engine.ReserveMath;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Nascimento, carência de 24h, reaparecimento e reorg do fundamento (CA-011..013, CA-020..024, PET-13).
 */
@QuarkusTest
class PetEngineEggTest {

    private static final Instant NOW = Instant.parse("2026-09-13T15:00:00Z");
    private static final long PORTION_SATS = 20_000L;
    private static final long FIVE_THOUSAND = 5_000L;
    private static final BigDecimal FORTY_EIGHT_HOURS = hours("48");
    private static final BigDecimal TWENTY_FOUR_HOURS = hours("24");
    private static final BigDecimal SIX_HOURS = hours("6");

    @Inject
    PetLifecyclePort lifecycle;

    @Inject
    PetReferencePortionPort portionPort;

    @Test
    @Transactional
    void ca011SaldoZeroNoOvoNaoNasce() {
        Fixture fixture = persistEgg("ca011");

        lifecycle.onBalanceKnown(fixture.pet.id, 0L, 0L, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(PetPresentation.EGG, pet.presentation);
        assertNull(pet.bornAt);
    }

    @Test
    @Transactional
    void ca012PendenteNaoNasceNemLimpaOvo() {
        Fixture fixture = persistEgg("ca012");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);

        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, false, NOW);
        lifecycle.onBalanceKnown(fixture.pet.id, 0L, FIVE_THOUSAND, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(PetPresentation.EGG, pet.presentation);
        assertNull(pet.bornAt);
        assertEquals(ArtworkStatus.NONE, pet.artworkStatus);
    }

    @Test
    @Transactional
    void ca013SaldoConfirmadoNasceComArtePendenteEFicaNoOvo() {
        Fixture fixture = persistEgg("ca013");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);

        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, true, NOW);
        lifecycle.onBalanceKnown(fixture.pet.id, FIVE_THOUSAND, 0L, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(NOW, pet.bornAt);
        assertEquals(ArtworkStatus.PENDING, pet.artworkStatus);
        assertEquals(PetPresentation.EGG, pet.presentation);
        assertNull(pet.zeroBalanceSince);
    }

    @Test
    @Transactional
    void arteAprovadaNoPrimeiroNascimentoApresentaCriatura() {
        Fixture fixture = persistEgg("art-approved");
        Pet stored = Pet.findById(fixture.pet.id);
        stored.artworkStatus = ArtworkStatus.APPROVED;

        lifecycle.onBalanceKnown(fixture.pet.id, FIVE_THOUSAND, 0L, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(NOW, pet.bornAt);
        assertEquals(ArtworkStatus.APPROVED, pet.artworkStatus);
        assertEquals(PetPresentation.CREATURE, pet.presentation);
    }

    @Test
    @Transactional
    void ca020SaldoConfirmadoPositivoNaoIniciaCarencia() {
        Fixture fixture = persistBornCreature("ca020");
        Pet stored = Pet.findById(fixture.pet.id);
        stored.reserveHours = hours("6");
        stored.zeroBalanceSince = NOW.minus(Duration.ofHours(2));

        lifecycle.onBalanceKnown(fixture.pet.id, 1_000L, 0L, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        assertNull(pet.zeroBalanceSince);
        assertEquals(PetPresentation.CREATURE, pet.presentation);
        assertEquals(NOW, pet.bornAt);
    }

    @Test
    @Transactional
    void ca021SaldoZeroIniciaCarenciaETickAntesDe24hMantemCriatura() {
        Fixture fixture = persistBornCreature("ca021");
        Instant plus23h = NOW.plus(Duration.ofHours(23));

        lifecycle.onBalanceKnown(fixture.pet.id, 0L, 0L, NOW);
        Pet afterZero = Pet.findById(fixture.pet.id);
        assertEquals(NOW, afterZero.zeroBalanceSince);
        assertEquals(PetPresentation.CREATURE, afterZero.presentation);

        lifecycle.tick(fixture.pet.id, plus23h);

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(PetPresentation.CREATURE, pet.presentation);
        assertEquals(NOW, pet.bornAt);
        assertEquals(NOW, pet.zeroBalanceSince);
    }

    @Test
    @Transactional
    void ca022Apos24hVoltaAoOvoSemApagarNascimentoNemReserva() {
        Fixture fixture = persistBornCreature("ca022");
        Pet stored = Pet.findById(fixture.pet.id);
        stored.reserveHours = FORTY_EIGHT_HOURS;
        stored.artworkStatus = ArtworkStatus.APPROVED;
        Instant plus24h = NOW.plus(Duration.ofHours(24));

        lifecycle.onBalanceKnown(fixture.pet.id, 0L, 0L, NOW);
        lifecycle.tick(fixture.pet.id, plus24h);

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(PetPresentation.EGG, pet.presentation);
        assertEquals(NOW, pet.bornAt);
        assertEquals(ArtworkStatus.APPROVED, pet.artworkStatus);
        assertEquals(plus24h, pet.lastReturnedToEggAt);
        assertEquals(0, pet.reserveHours.compareTo(TWENTY_FOUR_HOURS),
                "retorno ao ovo não zera a reserva; o tick só consome o tempo decorrido");
    }

    @Test
    @Transactional
    void ca023SaldoPositivoAntesDe24hReiniciaCarencia() {
        Fixture fixture = persistBornCreature("ca023");
        Instant plus10h = NOW.plus(Duration.ofHours(10));
        Instant plus11h = NOW.plus(Duration.ofHours(11));

        lifecycle.onBalanceKnown(fixture.pet.id, 0L, 0L, NOW);
        lifecycle.onBalanceKnown(fixture.pet.id, 1_000L, 0L, plus10h);
        Pet afterPositive = Pet.findById(fixture.pet.id);
        assertNull(afterPositive.zeroBalanceSince);
        assertEquals(PetPresentation.CREATURE, afterPositive.presentation);

        lifecycle.onBalanceKnown(fixture.pet.id, 0L, 0L, plus11h);

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(plus11h, pet.zeroBalanceSince);
        assertEquals(PetPresentation.CREATURE, pet.presentation);
    }

    @Test
    @Transactional
    void ca024ReaparecimentoComArteAprovadaVoltaCriatura() {
        Fixture fixture = persistBornCreature("ca024");
        Pet stored = Pet.findById(fixture.pet.id);
        stored.artworkStatus = ArtworkStatus.APPROVED;
        Instant plus24h = NOW.plus(Duration.ofHours(24));
        Instant reappearAt = plus24h.plusSeconds(60);

        lifecycle.onBalanceKnown(fixture.pet.id, 0L, 0L, NOW);
        lifecycle.tick(fixture.pet.id, plus24h);
        lifecycle.onBalanceKnown(fixture.pet.id, FIVE_THOUSAND, 0L, reappearAt);

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(PetPresentation.CREATURE, pet.presentation);
        assertEquals(NOW, pet.bornAt);
        assertEquals(ArtworkStatus.APPROVED, pet.artworkStatus);
        assertEquals(reappearAt, pet.lastReappearedAt);
        assertNull(pet.zeroBalanceSince);
    }

    @Test
    @Transactional
    void pet13InvalidarUltimaAlimentacaoValidaVoltaAoOvoNaHora() {
        Fixture fixture = persistBornCreature("pet13");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);
        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, true, NOW);

        lifecycle.onReceiptInvalidated(fixture.pet.id, receiptId, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        PetFeeding feeding = singleFeeding(pet);
        assertEquals(FeedingStatus.INVALIDATED, feeding.status);
        assertEquals(PetPresentation.EGG, pet.presentation);
        assertEquals(NOW, pet.bornAt);
        assertEquals(NOW, pet.lastReturnedToEggAt);
        assertEquals(ArtworkStatus.NONE, pet.artworkStatus);
    }

    @Test
    @Transactional
    void pet13ReorgDaUltimaValidaNaCriaturaVoltaAoOvoEMantemHoras() {
        Fixture fixture = persistBornCreature("pet13-reorg");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);
        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, true, NOW);
        Pet afterConfirm = Pet.findById(fixture.pet.id);
        assertEquals(0, afterConfirm.reserveHours.compareTo(SIX_HOURS));
        assertEquals(0, singleFeeding(afterConfirm).durationHours.compareTo(SIX_HOURS));

        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, false, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        PetFeeding feeding = singleFeeding(pet);
        assertEquals(FeedingStatus.PROVISIONAL, feeding.status);
        assertEquals(0, feeding.durationHours.compareTo(SIX_HOURS),
                "criatura já creditou na mempool — reorg não desfaz as horas");
        assertEquals(0, pet.reserveHours.compareTo(SIX_HOURS));
        assertEquals(PetPresentation.EGG, pet.presentation);
        assertEquals(NOW, pet.bornAt);
        assertEquals(NOW, pet.lastReturnedToEggAt);
    }

    @Test
    @Transactional
    void reorgDaValidaNoOvoDescreditaHorasEZeraDuracao() {
        Fixture fixture = persistEgg("reorg-egg");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);
        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, true, NOW);
        lifecycle.onBalanceKnown(fixture.pet.id, FIVE_THOUSAND, 0L, NOW);
        Pet afterBirth = Pet.findById(fixture.pet.id);
        assertEquals(NOW, afterBirth.bornAt);
        assertEquals(ArtworkStatus.PENDING, afterBirth.artworkStatus);
        assertEquals(PetPresentation.EGG, afterBirth.presentation);
        assertEquals(0, afterBirth.reserveHours.compareTo(SIX_HOURS));

        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, false, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        PetFeeding feeding = singleFeeding(pet);
        assertEquals(FeedingStatus.PROVISIONAL, feeding.status);
        assertEquals(0, feeding.durationHours.compareTo(BigDecimal.ZERO.setScale(ReserveMath.SCALE)));
        assertFalse(feeding.presentable);
        assertEquals(0, pet.reserveHours.compareTo(BigDecimal.ZERO.setScale(ReserveMath.SCALE)),
                "ovo descredita as horas adicionadas na confirmação");
        assertEquals(PetPresentation.EGG, pet.presentation);
        assertEquals(NOW, pet.bornAt);
    }

    @Test
    @Transactional
    void pendenteDuranteCarenciaNaoLimpaZeroBalanceSince() {
        Fixture fixture = persistBornCreature("grace-pending");
        Instant started = NOW;

        lifecycle.onBalanceKnown(fixture.pet.id, 0L, 0L, started);
        lifecycle.onBalanceKnown(fixture.pet.id, 0L, 1_000L, started.plus(Duration.ofHours(2)));

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(started, pet.zeroBalanceSince);
        assertEquals(PetPresentation.CREATURE, pet.presentation);
    }

    @Test
    @Transactional
    void falhaDeProvedorNaoIniciaCarenciaNemMudaApresentacao() {
        Fixture fixture = persistBornCreature("provider-egg");

        lifecycle.onProviderFailure(fixture.pet.id, NOW.plus(Duration.ofHours(2)));

        Pet pet = Pet.findById(fixture.pet.id);
        assertNull(pet.zeroBalanceSince);
        assertEquals(PetPresentation.CREATURE, pet.presentation);
        assertEquals(NOW, pet.bornAt);
    }

    private Fixture persistEgg(String marker) {
        return persistPet(marker, PetPresentation.EGG, null, true);
    }

    private Fixture persistBornCreature(String marker) {
        return persistPet(marker, PetPresentation.CREATURE, NOW, true);
    }

    private Fixture persistPet(String marker, PetPresentation presentation, Instant bornAt, boolean withPortion) {
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
        pet.presentation = presentation;
        pet.bornAt = bornAt;
        pet.persist();
        if (withPortion) {
            portionPort.recordPositivePortion(
                    pet.id, account.id, PORTION_SATS, PortionOrigin.CREATOR_PLAN, NOW);
        }
        return new Fixture(pet, account, address);
    }

    private static UUID persistReceipt(Fixture fixture, long amountSats) {
        LogicalReceipt receipt = LogicalReceipt.createPending(
                fixture.address,
                UUID.randomUUID().toString().replace("-", ""),
                amountSats,
                NOW
        );
        receipt.persist();
        return receipt.id;
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

    private record Fixture(Pet pet, Account account, Address address) {
    }
}
