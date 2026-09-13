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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fila de apresentação por conta e skip sem alterar reserva (PET-15, CA-034, CA-035).
 */
@QuarkusTest
class PetPresentationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T15:00:00Z");
    private static final long PORTION_SATS = 20_000L;
    private static final long FIVE_THOUSAND = 5_000L;

    @Inject
    PetPresentationService presentationService;

    @Inject
    PetLifecyclePort lifecycle;

    @Inject
    PetReferencePortionPort portionPort;

    @Test
    @Transactional
    void ca034TresAlimentacoesLiveAposVinculoAparecemNaFila() {
        Fixture fixture = persistCreatureWithPortion("ca034");

        lifecycle.onReceiptObserved(fixture.pet.id, persistReceipt(fixture, FIVE_THOUSAND), FIVE_THOUSAND, true, NOW);
        lifecycle.onReceiptObserved(fixture.pet.id, persistReceipt(fixture, FIVE_THOUSAND), FIVE_THOUSAND, true, NOW);
        lifecycle.onReceiptObserved(fixture.pet.id, persistReceipt(fixture, FIVE_THOUSAND), FIVE_THOUSAND, true, NOW);

        PresentationQueueResponse queue = presentationService.presentationQueue(fixture.account);

        assertEquals(3, queue.items().size());
        for (PresentationQueueItem item : queue.items()) {
            assertEquals("PET_FEEDING_APPLIED", item.eventType());
            assertEquals(FIVE_THOUSAND, item.amountSats());
            assertTrue(item.eventId() != null && !item.eventId().isBlank());
            assertTrue(item.occurredAt() != null && !item.occurredAt().isBlank());
        }
        String serialized = queue.items().toString();
        assertTrue(!serialized.toLowerCase().contains("petid"));
        assertTrue(!serialized.toLowerCase().contains("accountid"));
        assertTrue(!serialized.toLowerCase().contains("email"));
    }

    @Test
    @Transactional
    void ca035SkipEsvaziaFilaSemAlterarReservaNemHumor() {
        Fixture fixture = persistCreatureWithPortion("ca035");
        lifecycle.onReceiptObserved(fixture.pet.id, persistReceipt(fixture, FIVE_THOUSAND), FIVE_THOUSAND, true, NOW);
        lifecycle.onReceiptObserved(fixture.pet.id, persistReceipt(fixture, FIVE_THOUSAND), FIVE_THOUSAND, true, NOW);

        Pet before = Pet.findById(fixture.pet.id);
        BigDecimal reserveBefore = before.reserveHours;
        EmotionalState moodBefore = before.emotionalState;
        PetPresentation presentationBefore = before.presentation;

        SkipPresentationResponse skip = presentationService.skip(fixture.account);
        assertTrue(skip.skipped());

        PresentationCursor cursor = PresentationCursor.findByAccount(fixture.account).orElseThrow();
        assertTrue(cursor.lastPresentedEventId != null, "skip deve avançar o cursor até o último evento");

        PresentationQueueResponse queue = presentationService.presentationQueue(fixture.account);
        assertTrue(queue.items().isEmpty());

        Pet after = Pet.findById(fixture.pet.id);
        assertEquals(0, after.reserveHours.compareTo(reserveBefore));
        assertEquals(moodBefore, after.emotionalState);
        assertEquals(presentationBefore, after.presentation);
    }

    @Test
    @Transactional
    void alimentacaoHistoricaNuncaEntraNaFila() {
        Fixture fixture = persistCreatureWithPortion("ca014-hist");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);
        PetFeeding historical = PetFeeding.create(
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
        );
        historical.persist();
        UUID eventId = UUID.nameUUIDFromBytes(
                ("PET_FEEDING_APPLIED:" + historical.id).getBytes(StandardCharsets.UTF_8));
        OutboxEvent.createWithId(
                eventId,
                "Pet",
                fixture.pet.id.toString(),
                "PET_FEEDING_APPLIED",
                "{\"eventType\":\"PET_FEEDING_APPLIED\",\"occurredAt\":\"" + NOW + "\",\"amountSats\":5000}",
                NOW.plusSeconds(1),
                null
        ).persist();

        PresentationQueueResponse queue = presentationService.presentationQueue(fixture.account);

        assertTrue(queue.items().isEmpty());
    }

    @Test
    @Transactional
    void eventosAnterioresAoVinculoDestaContaNaoEntram() {
        Fixture fixture = persistCreatureWithPortion("bound-after");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);
        PetFeeding live = PetFeeding.create(
                fixture.pet,
                receiptId,
                FIVE_THOUSAND,
                PORTION_SATS,
                new BigDecimal("6").setScale(ReserveMath.SCALE),
                NOW.minus(Duration.ofDays(2)),
                FeedingStatus.VALID,
                FeedingOrigin.LIVE,
                true,
                NOW.minus(Duration.ofDays(2))
        );
        live.persist();
        UUID eventId = UUID.nameUUIDFromBytes(
                ("PET_FEEDING_APPLIED:" + live.id).getBytes(StandardCharsets.UTF_8));
        OutboxEvent.createWithId(
                eventId,
                "Pet",
                fixture.pet.id.toString(),
                "PET_FEEDING_APPLIED",
                "{\"eventType\":\"PET_FEEDING_APPLIED\",\"occurredAt\":\"" + NOW.minus(Duration.ofDays(2))
                        + "\",\"amountSats\":5000}",
                NOW.minus(Duration.ofDays(1)),
                null
        ).persist();

        PresentationQueueResponse queue = presentationService.presentationQueue(fixture.account);

        assertTrue(queue.items().isEmpty(), "evento com createdAt anterior a boundAt não entra");
    }

    @Test
    @Transactional
    void skipSemEventosENoOpComSkippedTrue() {
        Fixture fixture = persistCreatureWithPortion("skip-empty");

        SkipPresentationResponse skip = presentationService.skip(fixture.account);

        assertTrue(skip.skipped());
        assertTrue(presentationService.presentationQueue(fixture.account).items().isEmpty());
        assertTrue(PresentationCursor.findByAccount(fixture.account).isEmpty()
                || PresentationCursor.findByAccount(fixture.account).get().lastPresentedEventId == null);
    }

    @Test
    @Transactional
    void petBornAposVinculoEntraNaFila() {
        Fixture fixture = persistCreatureWithPortion("born-queue");
        OutboxEvent.create(
                "Pet",
                fixture.pet.id.toString(),
                "PET_BORN",
                "{\"eventType\":\"PET_BORN\",\"occurredAt\":\"" + NOW.plusSeconds(10) + "\"}",
                NOW.plusSeconds(10),
                null
        ).persist();

        PresentationQueueResponse queue = presentationService.presentationQueue(fixture.account);

        assertEquals(1, queue.items().size());
        assertEquals("PET_BORN", queue.items().getFirst().eventType());
        assertEquals(null, queue.items().getFirst().amountSats());
    }

    @Test
    @Transactional
    void alimentacaoInvalidadaSaiDaFila() {
        Fixture fixture = persistCreatureWithPortion("queue-invalid");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);
        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, true, NOW);
        assertEquals(1, presentationService.presentationQueue(fixture.account).items().size());

        lifecycle.onReceiptInvalidated(fixture.pet.id, receiptId, NOW);

        PresentationQueueResponse queue = presentationService.presentationQueue(fixture.account);
        assertTrue(queue.items().isEmpty(), "INVALIDATED não pode permanecer na fila de comemoração");
    }

    @Test
    @Transactional
    void revisaoVigenteEntraNaFila() {
        Fixture fixture = persistCreatureWithPortion("queue-revised");
        UUID receiptId = persistReceipt(fixture, FIVE_THOUSAND);
        lifecycle.onReceiptObserved(fixture.pet.id, receiptId, FIVE_THOUSAND, false, NOW);
        lifecycle.onReceiptRevised(fixture.pet.id, receiptId, 10_000L, NOW);

        List<String> types = presentationService.presentationQueue(fixture.account).items().stream()
                .map(PresentationQueueItem::eventType)
                .toList();
        assertTrue(types.contains("PET_FEEDING_APPLIED"));
        assertTrue(types.contains("PET_FEEDING_REVISED"), "REVISED vigente deve casar o id com amount/duration atuais");
    }

    private Fixture persistCreatureWithPortion(String marker) {
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
        pet.presentation = PetPresentation.CREATURE;
        pet.persist();
        portionPort.recordPositivePortion(pet.id, account.id, PORTION_SATS, PortionOrigin.CREATOR_PLAN, NOW);
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
