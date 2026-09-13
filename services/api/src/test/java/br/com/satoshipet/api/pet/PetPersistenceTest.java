package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.btc.LogicalReceipt;
import br.com.satoshipet.api.pet.engine.EmotionalState;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistência do schema V6 do motor do pet: pets ampliados, alimentações,
 * porção de referência e cursor de apresentação.
 */
@QuarkusTest
class PetPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T15:00:00Z");
    private static final BigDecimal TWENTY_FOUR_HOURS = new BigDecimal("24.0000000000");

    @Inject
    Flyway flyway;

    @Test
    void flywayCorrenteEhVersao6() {
        assertEquals("6", flyway.info().current().getVersion().getVersion());
    }

    @Test
    void persistePetViaCreateERecarregaEstadoInicialDoOvo() {
        UUID petId = QuarkusTransaction.requiringNew().call(() -> persistPet("ovo").pet.id);

        QuarkusTransaction.requiringNew().run(() -> {
            Pet reloaded = Pet.findById(petId);
            assertNotNull(reloaded);
            assertEquals(PetPresentation.EGG, reloaded.presentation);
            assertTrue(reloaded.awaitingReference);
            assertEquals(0, reloaded.reserveHours.compareTo(BigDecimal.ZERO));
            assertEquals(EmotionalState.ALIMENTADO, reloaded.emotionalState);
            assertEquals(ArtworkStatus.NONE, reloaded.artworkStatus);
            assertEquals(reloaded.creatorAccount.id, reloaded.foodSourceAccount.id);
        });
    }

    @Test
    void persisteAlimentacaoELocalizaPorPetERecebimento() {
        UUID[] ids = new UUID[2];
        QuarkusTransaction.requiringNew().run(() -> {
            Fixture fixture = persistPetWithReceipt("feed");
            ids[0] = fixture.pet.id;
            ids[1] = fixture.receipt.id;
            PetFeeding feeding = PetFeeding.create(
                    fixture.pet,
                    fixture.receipt.id,
                    10_000L,
                    1_000L,
                    TWENTY_FOUR_HOURS,
                    NOW,
                    FeedingStatus.VALID,
                    FeedingOrigin.LIVE,
                    true,
                    NOW
            );
            feeding.persist();
        });

        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(ids[0]);
            Optional<PetFeeding> found = PetFeeding.findByPetAndReceipt(pet, ids[1]);
            assertTrue(found.isPresent());
            assertEquals(ids[1], found.get().logicalReceiptId);
            assertEquals(10_000L, found.get().amountSats);
            assertEquals(FeedingStatus.VALID, found.get().status);
            assertEquals(FeedingOrigin.LIVE, found.get().origin);
            List<PetFeeding> listed = PetFeeding.listByPet(pet);
            assertEquals(1, listed.size());
            assertEquals(found.get().id, listed.get(0).id);
        });
    }

    @Test
    void segundaAlimentacaoComMesmoPetERecebimentoViolaUnicidade() {
        UUID[] ids = new UUID[2];
        QuarkusTransaction.requiringNew().run(() -> {
            Fixture fixture = persistPetWithReceipt("dup-feed");
            ids[0] = fixture.pet.id;
            ids[1] = fixture.receipt.id;
            PetFeeding.create(
                    fixture.pet,
                    fixture.receipt.id,
                    10_000L,
                    1_000L,
                    TWENTY_FOUR_HOURS,
                    NOW,
                    FeedingStatus.PROVISIONAL,
                    FeedingOrigin.LIVE,
                    false,
                    NOW
            ).persist();
        });

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                QuarkusTransaction.requiringNew().run(() -> {
                    Pet pet = Pet.findById(ids[0]);
                    PetFeeding.create(
                            pet,
                            ids[1],
                            8_000L,
                            1_000L,
                            TWENTY_FOUR_HOURS,
                            NOW,
                            FeedingStatus.VALID,
                            FeedingOrigin.HISTORICAL_RECONSTRUCTION,
                            true,
                            NOW
                    ).persist();
                    PetFeeding.flush();
                }));
        assertTrue(containsPersistenceException(thrown),
                "Esperado PersistenceException por unicidade (pet, receipt): " + thrown);
    }

    @Test
    void cursorDeApresentacaoEhUnicoPorConta() {
        UUID accountId = QuarkusTransaction.requiringNew().call(() -> {
            Fixture fixture = persistPet("cursor");
            PresentationCursor.create(fixture.account, NOW).persist();
            return fixture.account.id;
        });

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                QuarkusTransaction.requiringNew().run(() -> {
                    Account account = Account.findById(accountId);
                    PresentationCursor.create(account, NOW).persist();
                    PresentationCursor.flush();
                }));
        assertTrue(containsPersistenceException(thrown),
                "Esperado PersistenceException por unicidade de cursor por conta: " + thrown);

        QuarkusTransaction.requiringNew().run(() -> {
            Account account = Account.findById(accountId);
            Optional<PresentationCursor> found = PresentationCursor.findByAccount(account);
            assertTrue(found.isPresent());
            assertEquals(accountId, found.get().account.id);
        });
    }

    @Test
    void latestForPetRetornaPorcaoComValidFromMaisRecente() {
        UUID petId = QuarkusTransaction.requiringNew().call(() -> {
            Fixture fixture = persistPet("portion");
            PetReferencePortion.create(
                    fixture.pet,
                    fixture.account,
                    1_000L,
                    NOW.minusSeconds(3600),
                    PortionOrigin.CREATOR_PLAN,
                    NOW
            ).persist();
            PetReferencePortion.create(
                    fixture.pet,
                    fixture.account,
                    2_000L,
                    NOW,
                    PortionOrigin.FALLBACK_OLDEST_BINDING,
                    NOW
            ).persist();
            return fixture.pet.id;
        });

        QuarkusTransaction.requiringNew().run(() -> {
            Pet pet = Pet.findById(petId);
            Optional<PetReferencePortion> latest = PetReferencePortion.latestForPet(pet);
            assertTrue(latest.isPresent());
            assertEquals(2_000L, latest.get().portionSats);
            assertEquals(PortionOrigin.FALLBACK_OLDEST_BINDING, latest.get().origin);
        });
    }

    private static Fixture persistPet(String marker) {
        Address address = Address.create(uniqueCanonical(marker), NOW);
        address.persist();
        Account account = Account.create(
                marker + "-" + UUID.randomUUID() + "@test.com",
                "America/Sao_Paulo",
                "pt-BR",
                NOW
        );
        account.persist();
        Pet pet = Pet.create(address, account, "Pixel-" + marker, NOW);
        pet.persist();
        return new Fixture(pet, account, address, null);
    }

    private static Fixture persistPetWithReceipt(String marker) {
        Fixture base = persistPet(marker);
        LogicalReceipt receipt = LogicalReceipt.createPending(
                base.address,
                UUID.randomUUID().toString().replace("-", ""),
                10_000L,
                NOW
        );
        receipt.persist();
        return new Fixture(base.pet, base.account, base.address, receipt);
    }

    private static String uniqueCanonical(String marker) {
        return "bcrt1q" + marker.replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private static boolean containsPersistenceException(Throwable thrown) {
        Throwable current = thrown;
        while (current != null) {
            if (current instanceof PersistenceException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record Fixture(Pet pet, Account account, Address address, LogicalReceipt receipt) {
    }
}
