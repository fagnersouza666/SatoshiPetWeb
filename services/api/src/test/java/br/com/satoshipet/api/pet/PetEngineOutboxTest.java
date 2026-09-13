package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.btc.BitcoinTransaction;
import br.com.satoshipet.api.btc.LogicalReceipt;
import br.com.satoshipet.api.outbox.OutboxEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Eventos PET na outbox na mesma TX do motor (PET-16, CA-009, CA-014).
 */
@QuarkusTest
class PetEngineOutboxTest {

    private static final Instant NOW = Instant.parse("2026-09-13T15:00:00Z");
    private static final long PORTION_SATS = 20_000L;
    private static final long FIVE_THOUSAND = 5_000L;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Inject
    PetLifecyclePort lifecycle;

    @Inject
    PetReferencePortionPort portionPort;

    @Test
    @Transactional
    void alimentacaoLiveNaCriaturaEmitePetFeedingAppliedPublico() throws Exception {
        Fixture fixture = persistCreatureWithPortion("outbox-applied");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);

        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, true, NOW);

        List<OutboxEvent> applied = eventsOf(fixture.pet, "PET_FEEDING_APPLIED");
        assertEquals(1, applied.size());
        Map<String, Object> payload = MAPPER.readValue(applied.getFirst().payload, MAP_TYPE);
        assertEquals(fixture.address.canonical, payload.get("address"));
        assertEquals("PET_FEEDING_APPLIED", payload.get("eventType"));
        assertFalse(payload.containsKey("petId"));
        assertFalse(payload.containsKey("accountId"));
        assertFalse(payload.containsKey("email"));
        assertFalse(payload.containsKey("foodSourceAccountId"));
        assertEquals("Pet", applied.getFirst().aggregateType);
        assertEquals(fixture.pet.id.toString(), applied.getFirst().aggregateId);
    }

    @Test
    @Transactional
    void confirmacaoComMesmasHorasNaoEmiteSegundoAppliedNemRevised() {
        Fixture fixture = persistCreatureWithPortion("outbox-confirm");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);

        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, false, NOW);
        assertEquals(1, eventsOf(fixture.pet, "PET_FEEDING_APPLIED").size());

        lifecycle.onReceiptConfirmed(fixture.pet.id, receiptId, FIVE_THOUSAND, NOW);

        assertEquals(1, eventsOf(fixture.pet, "PET_FEEDING_APPLIED").size());
        assertTrue(eventsOf(fixture.pet, "PET_FEEDING_REVISED").isEmpty());
    }

    @Test
    @Transactional
    void invalidarAlimentacaoLiveEmitePetFeedingInvalidated() {
        Fixture fixture = persistCreatureWithPortion("outbox-invalid");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);
        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, true, NOW);

        lifecycle.onReceiptInvalidated(fixture.pet.id, receiptId, NOW);

        assertEquals(1, eventsOf(fixture.pet, "PET_FEEDING_INVALIDATED").size());
    }

    @Test
    @Transactional
    void primeiroSaldoConfirmadoComArteAprovadaEmitePetBorn() {
        Fixture fixture = persistPet("outbox-born", PetPresentation.EGG, true);
        Pet stored = Pet.findById(fixture.pet.id);
        stored.artworkStatus = ArtworkStatus.APPROVED;

        lifecycle.onBalanceKnown(fixture.pet.id, FIVE_THOUSAND, 0L, NOW);

        List<OutboxEvent> born = eventsOf(fixture.pet, "PET_BORN");
        assertEquals(1, born.size());
        assertEquals("Pet", born.getFirst().aggregateType);
    }

    @Test
    @Transactional
    void reconstructHistoricoNaoEmiteEventosDeAlimentacao() {
        Fixture fixture = persistPet("outbox-recon", PetPresentation.EGG, true);
        persistConfirmedReceipt(fixture, FIVE_THOUSAND, 100, NOW.minusNanos(1));
        persistConfirmedReceipt(fixture, 10_000L, 101, NOW.minusNanos(1));

        lifecycle.reconstruct(fixture.pet.id, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        List<PetFeeding> feedings = PetFeeding.listByPet(pet);
        assertEquals(2, feedings.size(), "reconstruct deve criar as alimentações históricas");
        assertTrue(feedings.stream().allMatch(f -> f.origin == FeedingOrigin.HISTORICAL_RECONSTRUCTION));
        List<OutboxEvent> feedingEvents = petEvents(pet).stream()
                .filter(event -> event.eventType.startsWith("PET_FEEDING_"))
                .toList();
        assertTrue(feedingEvents.isEmpty(), "reconstrução histórica não emite PET_FEEDING_* (CA-014)");
    }

    @Test
    @Transactional
    void reaparecimentoNoOvoComArtePendenteNaoEmitePetReappeared() {
        Fixture fixture = persistPet("outbox-reapp-egg", PetPresentation.EGG, true);
        Pet stored = Pet.findById(fixture.pet.id);
        stored.bornAt = NOW.minusSeconds(3600);
        stored.artworkStatus = ArtworkStatus.PENDING;
        stored.presentation = PetPresentation.EGG;

        lifecycle.onBalanceKnown(fixture.pet.id, FIVE_THOUSAND, 0L, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(PetPresentation.EGG, pet.presentation);
        assertNotNull(pet.lastReappearedAt);
        assertTrue(eventsOf(pet, "PET_REAPPEARED").isEmpty());
    }

    @Test
    @Transactional
    void reaparecimentoOvoParaCriaturaEmitePetReappeared() {
        Fixture fixture = persistPet("outbox-reapp-ok", PetPresentation.EGG, true);
        Pet stored = Pet.findById(fixture.pet.id);
        stored.bornAt = NOW.minusSeconds(3600);
        stored.artworkStatus = ArtworkStatus.APPROVED;
        stored.presentation = PetPresentation.EGG;

        lifecycle.onBalanceKnown(fixture.pet.id, FIVE_THOUSAND, 0L, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(PetPresentation.CREATURE, pet.presentation);
        assertEquals(1, eventsOf(pet, "PET_REAPPEARED").size());
    }

    @Test
    @Transactional
    void primeiraPorcaoSemHistoricoEmitePetStateChanged() {
        Fixture fixture = persistPet("outbox-await", PetPresentation.EGG, false);
        Pet beforePortion = Pet.findById(fixture.pet.id);
        assertTrue(beforePortion.awaitingReference);

        portionPort.recordPositivePortion(
                fixture.pet.id, fixture.account.id, PORTION_SATS, PortionOrigin.CREATOR_PLAN, NOW);

        Pet pet = Pet.findById(fixture.pet.id);
        assertFalse(pet.awaitingReference);
        assertEquals(1, eventsOf(pet, "PET_STATE_CHANGED").size());
        assertFalse(petEvents(pet).isEmpty(), "primeira porção sem histórico não pode gerar zero eventos");
    }

    @Test
    @Transactional
    void reserveHoursNoPayloadEStringDecimal() throws Exception {
        Fixture fixture = persistCreatureWithPortion("outbox-hours");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);

        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, true, NOW);

        OutboxEvent applied = eventsOf(fixture.pet, "PET_FEEDING_APPLIED").getFirst();
        JsonNode node = MAPPER.readTree(applied.payload);
        assertTrue(node.get("reserveHours").isTextual(), "reserveHours deve ser string (CC-10)");
        assertFalse(node.get("reserveHours").isNumber());
    }

    private static List<OutboxEvent> eventsOf(Pet pet, String eventType) {
        return petEvents(pet).stream().filter(event -> eventType.equals(event.eventType)).toList();
    }

    private static List<OutboxEvent> petEvents(Pet pet) {
        return OutboxEvent.list("aggregateType = ?1 AND aggregateId = ?2", "Pet", pet.id.toString());
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
