package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.btc.LogicalReceipt;
import br.com.satoshipet.api.outbox.OutboxEvent;
import br.com.satoshipet.api.pet.engine.EmotionalState;
import br.com.satoshipet.api.pet.engine.ReserveMath;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Estatísticas CC-21: idade desde o nascimento original e origem reconstruída vs observada.
 */
@QuarkusTest
class PetStatsServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T15:00:00Z");
    private static final long PORTION_SATS = 20_000L;
    private static final long FIVE_THOUSAND = 5_000L;

    @Inject
    PetStatsService statsService;

    @Inject
    PetReferencePortionPort portionPort;

    @Test
    @Transactional
    void nuncaNasceuDevolveBornAtEIdadeNulos() {
        Fixture fixture = persistPet("stats-egg", false);

        PetStatsResponse stats = statsService.stats(fixture.account);

        assertNull(stats.bornAt());
        assertNull(stats.ageHours());
        assertFalse(stats.reconstructedPeriod());
        assertFalse(stats.observedPeriod());
        assertEquals("0.0000000000", stats.timeInStateHours().ALIMENTADO());
    }

    @Test
    @Transactional
    void idadeContaDesdeBornAtENaoZeraNoOvo() {
        Instant bornAt = Instant.now().minus(Duration.ofHours(48));
        Fixture fixture = persistPet("stats-age", false);
        Pet pet = Pet.findById(fixture.pet.id);
        pet.bornAt = bornAt;
        pet.presentation = PetPresentation.EGG;
        pet.lastReturnedToEggAt = Instant.now().minus(Duration.ofHours(1));

        PetStatsResponse stats = statsService.stats(fixture.account);

        assertEquals(bornAt.toString(), stats.bornAt());
        BigDecimal age = new BigDecimal(stats.ageHours());
        assertTrue(age.compareTo(new BigDecimal("47.9900000000")) >= 0, age.toPlainString());
        assertTrue(age.compareTo(new BigDecimal("48.0200000000")) <= 0, age.toPlainString());
    }

    @Test
    @Transactional
    void reconstructedPeriodQuandoHaAlimentacaoHistorica() {
        Fixture fixture = persistPet("stats-hist", true);
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);
        PetFeeding.create(
                fixture.pet,
                receiptId,
                FIVE_THOUSAND,
                PORTION_SATS,
                new BigDecimal("6").setScale(ReserveMath.SCALE),
                NOW,
                FeedingStatus.VALID,
                FeedingOrigin.HISTORICAL_RECONSTRUCTION,
                false,
                NOW
        ).persist();

        PetStatsResponse stats = statsService.stats(fixture.account);

        assertTrue(stats.reconstructedPeriod());
        assertFalse(stats.observedPeriod());
    }

    @Test
    @Transactional
    void observedPeriodQuandoHaAlimentacaoLive() {
        Fixture fixture = persistPet("stats-live", true);
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);
        PetFeeding.create(
                fixture.pet,
                receiptId,
                FIVE_THOUSAND,
                PORTION_SATS,
                new BigDecimal("6").setScale(ReserveMath.SCALE),
                NOW,
                FeedingStatus.VALID,
                FeedingOrigin.LIVE,
                true,
                NOW
        ).persist();

        PetStatsResponse stats = statsService.stats(fixture.account);

        assertTrue(stats.observedPeriod());
        assertFalse(stats.reconstructedPeriod());
    }

    @Test
    @Transactional
    void tempoPorEstadoReconstroiOutboxAteAgora() {
        Instant bornAt = Instant.now().minus(Duration.ofHours(5));
        Instant switched = bornAt.plus(Duration.ofHours(2));
        Fixture fixture = persistPet("stats-time", false);
        Pet pet = Pet.findById(fixture.pet.id);
        pet.bornAt = bornAt;
        pet.presentation = PetPresentation.CREATURE;
        pet.emotionalState = EmotionalState.PENSANDO;
        pet.lastEvaluatedAt = switched;

        OutboxEvent.create(
                "Pet",
                pet.id.toString(),
                "PET_BORN",
                "{\"eventType\":\"PET_BORN\",\"occurredAt\":\"" + bornAt
                        + "\",\"presentation\":\"CREATURE\",\"emotionalState\":\"ALIMENTADO\"}",
                bornAt,
                null
        ).persist();
        OutboxEvent.create(
                "Pet",
                pet.id.toString(),
                "PET_STATE_CHANGED",
                "{\"eventType\":\"PET_STATE_CHANGED\",\"occurredAt\":\"" + switched
                        + "\",\"presentation\":\"CREATURE\",\"emotionalState\":\"PENSANDO\"}",
                switched,
                null
        ).persist();

        PetStatsResponse stats = statsService.stats(fixture.account);

        BigDecimal alimentado = new BigDecimal(stats.timeInStateHours().ALIMENTADO());
        BigDecimal pensando = new BigDecimal(stats.timeInStateHours().PENSANDO());
        assertTrue(alimentado.compareTo(new BigDecimal("1.9900000000")) >= 0, alimentado.toPlainString());
        assertTrue(alimentado.compareTo(new BigDecimal("2.0100000000")) <= 0, alimentado.toPlainString());
        assertTrue(pensando.compareTo(new BigDecimal("2.9900000000")) >= 0, pensando.toPlainString());
        assertTrue(pensando.compareTo(new BigDecimal("3.0200000000")) <= 0, pensando.toPlainString());
        assertEquals("0.0000000000", stats.timeInStateHours().HIBERNANDO());
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

    private static String uniqueCanonical(String marker) {
        return "bcrt1q" + marker.replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private record Fixture(Pet pet, Account account, Address address) {
    }
}
