package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.btc.LogicalReceipt;
import br.com.satoshipet.api.pet.engine.EmotionalState;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Motor de alimentação persistente: reserva BigDecimal, CC-11, CC-14 lite e
 * idempotência por recebimento lógico (CA-015, CA-017, CA-018, CA-026, CA-028).
 */
@QuarkusTest
class PetEngineTest {

    private static final Instant NOW = Instant.parse("2026-09-13T15:00:00Z");
    private static final long PORTION_SATS = 20_000L;
    private static final long FIVE_THOUSAND = 5_000L;
    private static final BigDecimal SIX_HOURS = hours("6");
    private static final BigDecimal THREE_HOURS = hours("3");
    private static final BigDecimal TWELVE_HOURS = hours("12");
    private static final BigDecimal TWENTY_FOUR_HOURS = hours("24");

    @Inject
    PetLifecyclePort lifecycle;

    @Inject
    PetReferencePortionPort portionPort;

    @Test
    @Transactional
    void recebimentoConfirmadoDeCincoMilSatsAdicionaSeisHorasValidas() {
        Fixture fixture = persistCreatureWithPortion("ca015");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);

        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, true, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        List<PetFeeding> feedings = PetFeeding.listByPet(pet);
        assertEquals(1, feedings.size());
        PetFeeding feeding = feedings.get(0);
        assertEquals(receiptId, feeding.logicalReceiptId);
        assertEquals(FIVE_THOUSAND, feeding.amountSats);
        assertEquals(PORTION_SATS, feeding.portionSats);
        assertEquals(0, feeding.durationHours.compareTo(SIX_HOURS));
        assertEquals(FeedingStatus.VALID, feeding.status);
        assertEquals(FeedingOrigin.LIVE, feeding.origin);
        assertTrue(feeding.presentable);
        assertEquals(0, pet.reserveHours.compareTo(SIX_HOURS));
    }

    @Test
    @Transactional
    void segundoPollDoMesmoRecebimentoNaoDuplicaAlimentacaoNemHoras() {
        Fixture fixture = persistCreatureWithPortion("ca017");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);

        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, true, NOW);
        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, true, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(1, PetFeeding.listByPet(pet).size());
        assertEquals(0, pet.reserveHours.compareTo(SIX_HOURS));
    }

    @Test
    @Transactional
    void semPorcaoDeReferenciaNaoCriaAlimentacaoNemAlteraReserva() {
        Fixture fixture = persistPet("ca026", PetPresentation.CREATURE, false);
        UUID receiptId = UUID.randomUUID();

        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, true, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        assertTrue(PetFeeding.listByPet(pet).isEmpty());
        assertEquals(0, pet.reserveHours.compareTo(BigDecimal.ZERO));
        assertTrue(pet.awaitingReference);
    }

    @Test
    @Transactional
    void criaturaPendenteCreditaProvisorioEConfirmacaoNaoSomaDeNovo() {
        Fixture fixture = persistCreatureWithPortion("ca028");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);

        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, false, NOW);
        Pet afterPending = Pet.findById(fixture.pet.id);
        PetFeeding pending = singleFeeding(afterPending);
        assertEquals(FeedingStatus.PROVISIONAL, pending.status);
        assertEquals(0, pending.durationHours.compareTo(SIX_HOURS));
        assertTrue(pending.presentable);
        assertEquals(0, afterPending.reserveHours.compareTo(SIX_HOURS));

        lifecycle.onReceiptConfirmed(fixture.pet.id, receiptId, FIVE_THOUSAND, NOW);

        Pet afterConfirm = Pet.findById(fixture.pet.id);
        PetFeeding confirmed = singleFeeding(afterConfirm);
        assertEquals(FeedingStatus.VALID, confirmed.status);
        assertEquals(0, confirmed.durationHours.compareTo(SIX_HOURS));
        assertEquals(0, afterConfirm.reserveHours.compareTo(SIX_HOURS));
    }

    @Test
    @Transactional
    void confirmacaoDaCriaturaNaoRecalculaHorasQuandoPorcaoDeReferenciaMuda() {
        Fixture fixture = persistCreatureWithPortion("ca028-portion");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);

        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, false, NOW);
        assertEquals(0, ((Pet) Pet.findById(fixture.pet.id)).reserveHours.compareTo(SIX_HOURS));

        portionPort.recordPositivePortion(
                fixture.pet.id,
                fixture.account.id,
                10_000L,
                PortionOrigin.CREATOR_PLAN,
                NOW.plusSeconds(1)
        );

        lifecycle.onReceiptConfirmed(fixture.pet.id, receiptId, FIVE_THOUSAND, NOW.plusSeconds(2));

        Pet pet = Pet.findById(fixture.pet.id);
        PetFeeding feeding = singleFeeding(pet);
        assertEquals(FeedingStatus.VALID, feeding.status);
        assertEquals(PORTION_SATS, feeding.portionSats);
        assertEquals(FIVE_THOUSAND, feeding.amountSats);
        assertEquals(0, feeding.durationHours.compareTo(SIX_HOURS));
        assertEquals(0, pet.reserveHours.compareTo(SIX_HOURS),
                "confirmação da criatura não pode recalcular com a nova porção");
    }

    @Test
    @Transactional
    void ovoPendenteNaoCreditaHorasEConfirmacaoAdicionaSeisHorasValidas() {
        Fixture fixture = persistPet("egg-cc14", PetPresentation.EGG, true);
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);

        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, false, NOW);

        Pet afterPending = Pet.findById(fixture.pet.id);
        PetFeeding pending = singleFeeding(afterPending);
        assertEquals(FeedingStatus.PROVISIONAL, pending.status);
        assertEquals(0, pending.durationHours.compareTo(BigDecimal.ZERO.setScale(10)));
        assertFalse(pending.presentable);
        assertEquals(0, afterPending.reserveHours.compareTo(BigDecimal.ZERO));
        assertEquals(PetPresentation.EGG, afterPending.presentation);

        lifecycle.onReceiptConfirmed(fixture.pet.id, receiptId, FIVE_THOUSAND, NOW.plusSeconds(30));

        Pet afterConfirm = Pet.findById(fixture.pet.id);
        PetFeeding confirmed = singleFeeding(afterConfirm);
        assertEquals(FeedingStatus.VALID, confirmed.status);
        assertEquals(0, confirmed.durationHours.compareTo(SIX_HOURS));
        assertTrue(confirmed.presentable);
        assertEquals(0, afterConfirm.reserveHours.compareTo(SIX_HOURS));
        assertEquals(PetPresentation.EGG, afterConfirm.presentation);
    }

    @Test
    @Transactional
    void tickLongeNoFuturoComReservaZeroNaoGeraDividaAoCreditarPorcaoCheia() {
        Fixture fixture = persistCreatureWithPortion("ca018");
        Instant farFuture = NOW.plus(Duration.ofHours(100));

        lifecycle.tick(fixture.pet.id, farFuture);
        lifecycle.onReceiptConfirmed(fixture.pet.id, persistReceipt(fixture, PORTION_SATS), PORTION_SATS, farFuture);

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(0, pet.reserveHours.compareTo(TWENTY_FOUR_HOURS),
                "fome não reduz o crédito da porção cheia");
    }

    @Test
    @Transactional
    void tickTresHorasAposReservaDeSeisMantemAlimentadoComTresHoras() {
        Fixture fixture = persistCreatureWithPortion("tick-3h");
        lifecycle.onReceiptObserved(fixture.pet.id, persistReceipt(fixture, FIVE_THOUSAND), FIVE_THOUSAND, true, NOW);

        lifecycle.tick(fixture.pet.id, NOW.plus(Duration.ofHours(3)));

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(0, pet.reserveHours.compareTo(THREE_HOURS));
        assertEquals(EmotionalState.ALIMENTADO, pet.emotionalState);
    }

    @Test
    @Transactional
    void tickDezHorasAposReservaDeSeisEsgotaReservaEFicaPensando() {
        Fixture fixture = persistCreatureWithPortion("tick-10h");
        lifecycle.onReceiptObserved(fixture.pet.id, persistReceipt(fixture, FIVE_THOUSAND), FIVE_THOUSAND, true, NOW);

        lifecycle.tick(fixture.pet.id, NOW.plus(Duration.ofHours(10)));

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(0, pet.reserveHours.compareTo(BigDecimal.ZERO.setScale(10)));
        assertEquals(EmotionalState.PENSANDO, pet.emotionalState);
    }

    @Test
    @Transactional
    void revisaoDeCincoParaDezMilNaCriaturaAumentaDuracaoEReservaEmSeisHoras() {
        Fixture fixture = persistCreatureWithPortion("rbf");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);
        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, false, NOW);

        lifecycle.onReceiptRevised(fixture.pet.id, receiptId, 10_000L, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        PetFeeding feeding = singleFeeding(pet);
        assertEquals(10_000L, feeding.amountSats);
        assertEquals(0, feeding.durationHours.compareTo(TWELVE_HOURS));
        assertEquals(0, pet.reserveHours.compareTo(TWELVE_HOURS));
    }

    @Test
    @Transactional
    void invalidarAlimentacaoCreditadaReduzReservaEMarcaInvalidada() {
        Fixture fixture = persistCreatureWithPortion("invalidate");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);
        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, true, NOW);

        lifecycle.onReceiptInvalidated(fixture.pet.id, receiptId, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        PetFeeding feeding = singleFeeding(pet);
        assertEquals(FeedingStatus.INVALIDATED, feeding.status);
        assertEquals(0, pet.reserveHours.compareTo(BigDecimal.ZERO.setScale(10)));
    }

    @Test
    @Transactional
    void falhaDeProvedorNaoAlteraReservaNemCarenciaDeSaldoZero() {
        Fixture fixture = persistCreatureWithPortion("provider");
        Pet stored = Pet.findById(fixture.pet.id);
        stored.zeroBalanceSince = NOW;
        stored.reserveHours = SIX_HOURS;
        Instant lastEvaluated = stored.lastEvaluatedAt;

        lifecycle.onProviderFailure(fixture.pet.id, NOW.plus(Duration.ofHours(2)));

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(0, pet.reserveHours.compareTo(SIX_HOURS));
        assertEquals(NOW, pet.zeroBalanceSince);
        assertEquals(lastEvaluated, pet.lastEvaluatedAt);
    }

    @Test
    @Transactional
    void petInexistenteLancaIllegalArgumentException() {
        UUID missing = UUID.randomUUID();
        Instant when = NOW;

        IllegalArgumentException observed = assertThrows(
                IllegalArgumentException.class,
                () -> lifecycle.onReceiptObserved(missing, UUID.randomUUID(), FIVE_THOUSAND, true, when)
        );
        assertTrue(observed.getMessage().contains("Pet não encontrado"), observed.getMessage());

        IllegalArgumentException tick = assertThrows(
                IllegalArgumentException.class,
                () -> lifecycle.tick(missing, when)
        );
        assertTrue(tick.getMessage().contains("Pet não encontrado"), tick.getMessage());
    }

    private Fixture persistCreatureWithPortion(String marker) {
        return persistPet(marker, PetPresentation.CREATURE, true);
    }

    private Fixture persistPet(String marker, PetPresentation presentation, boolean withPortion) {
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
        return new BigDecimal(value).setScale(10);
    }

    private static String uniqueCanonical(String marker) {
        return "bcrt1q" + marker.replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private record Fixture(Pet pet, Account account, Address address) {
    }
}
