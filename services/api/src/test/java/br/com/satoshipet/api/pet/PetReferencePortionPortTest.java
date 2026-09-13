package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.account.Address;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Porta de porção de referência (CC-05, CC-11): fonte do criador, fallback
 * ao vínculo mais antigo e última porção positiva sem inventar DCA.
 */
@QuarkusTest
class PetReferencePortionPortTest {

    private static final Instant NOW = Instant.parse("2026-09-13T15:00:00Z");
    private static final long CREATOR_PORTION = 20_000L;
    private static final long SECONDARY_PORTION = 50_000L;
    private static final long FALLBACK_PORTION = 15_000L;

    @Inject
    PetReferencePortionPort port;

    @Test
    @Transactional
    void semSnapshotESemLastPositiveRetornaVazio() {
        UUID petId = persistPetWithBinding("empty").pet.id;

        Optional<PetReferencePortionPort.ResolvedPortion> current =
                port.currentPositivePortion(petId);

        assertTrue(current.isEmpty());
        Pet pet = Pet.findById(petId);
        assertTrue(pet.awaitingReference);
        assertNull(pet.lastPositivePortionSats);
    }

    @Test
    @Transactional
    void recordPositivePortionAtualizaCurrentEFlagsDoPet() {
        Fixture fixture = persistPetWithBinding("record");

        port.recordPositivePortion(
                fixture.pet.id,
                fixture.account.id,
                CREATOR_PORTION,
                PortionOrigin.CREATOR_PLAN,
                NOW
        );

        Optional<PetReferencePortionPort.ResolvedPortion> current =
                port.currentPositivePortion(fixture.pet.id);
        assertTrue(current.isPresent());
        assertEquals(CREATOR_PORTION, current.get().portionSats());
        assertEquals(PortionOrigin.CREATOR_PLAN, current.get().origin());
        assertEquals(fixture.account.id, current.get().sourceAccountId());

        Pet pet = Pet.findById(fixture.pet.id);
        assertFalse(pet.awaitingReference);
        assertEquals(CREATOR_PORTION, pet.lastPositivePortionSats);
        assertEquals(PortionOrigin.CREATOR_PLAN, pet.lastPositivePortionOrigin);
        assertEquals(NOW, pet.updatedAt);
    }

    @Test
    @Transactional
    void snapshotDaContaSecundariaNaoMudaPorcaoEnquantoCriadorEhFonte() {
        Fixture fixture = persistPetWithBinding("cc05");
        Account secondary = persistAccount("secondary");
        AccountAddressBinding.create(
                secondary, fixture.address, false, NOW.plusSeconds(60)).persist();

        port.recordPositivePortion(
                fixture.pet.id,
                fixture.account.id,
                CREATOR_PORTION,
                PortionOrigin.CREATOR_PLAN,
                NOW
        );
        port.recordPositivePortion(
                fixture.pet.id,
                secondary.id,
                SECONDARY_PORTION,
                PortionOrigin.FALLBACK_OLDEST_BINDING,
                NOW.plusSeconds(120)
        );

        Optional<PetReferencePortionPort.ResolvedPortion> current =
                port.currentPositivePortion(fixture.pet.id);
        assertTrue(current.isPresent());
        assertEquals(CREATOR_PORTION, current.get().portionSats());
        assertEquals(PortionOrigin.CREATOR_PLAN, current.get().origin());
        assertEquals(fixture.account.id, current.get().sourceAccountId());
        assertEquals(fixture.account.id, ((Pet) Pet.findById(fixture.pet.id)).foodSourceAccount.id);
    }

    @Test
    @Transactional
    void aposUnbindDoCriadorUsaSnapshotDaFonteMaisAntigaRestante() {
        Fixture fixture = persistPetWithBinding("fallback-snap");
        Account remaining = persistAccount("remaining");
        AccountAddressBinding.create(
                remaining, fixture.address, false, NOW.plusSeconds(30)).persist();

        port.recordPositivePortion(
                fixture.pet.id,
                fixture.account.id,
                CREATOR_PORTION,
                PortionOrigin.CREATOR_PLAN,
                NOW
        );
        port.recordPositivePortion(
                fixture.pet.id,
                remaining.id,
                FALLBACK_PORTION,
                PortionOrigin.FALLBACK_OLDEST_BINDING,
                NOW.plusSeconds(40)
        );

        fixture.binding.unbind(NOW.plusSeconds(90));

        Optional<PetReferencePortionPort.ResolvedPortion> current =
                port.currentPositivePortion(fixture.pet.id);
        assertTrue(current.isPresent());
        assertEquals(FALLBACK_PORTION, current.get().portionSats());
        assertEquals(PortionOrigin.FALLBACK_OLDEST_BINDING, current.get().origin());
        assertEquals(remaining.id, current.get().sourceAccountId());
        assertEquals(remaining.id, ((Pet) Pet.findById(fixture.pet.id)).foodSourceAccount.id);
    }

    @Test
    @Transactional
    void aposUnbindDoCriadorSemSnapshotDaNovaFonteUsaLastPositiveDoPet() {
        Fixture fixture = persistPetWithBinding("fallback-last");
        Account remaining = persistAccount("remaining-last");
        AccountAddressBinding.create(
                remaining, fixture.address, false, NOW.plusSeconds(30)).persist();

        port.recordPositivePortion(
                fixture.pet.id,
                fixture.account.id,
                CREATOR_PORTION,
                PortionOrigin.CREATOR_PLAN,
                NOW
        );
        fixture.binding.unbind(NOW.plusSeconds(90));

        Optional<PetReferencePortionPort.ResolvedPortion> current =
                port.currentPositivePortion(fixture.pet.id);
        assertTrue(current.isPresent());
        assertEquals(CREATOR_PORTION, current.get().portionSats());
        assertEquals(PortionOrigin.CREATOR_PLAN, current.get().origin());
        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(remaining.id, pet.foodSourceAccount.id);
        assertEquals(CREATOR_PORTION, pet.lastPositivePortionSats);
    }

    @Test
    @Transactional
    void unbindDeTodosDeixaFoodSourceNuloEMantemLastPositive() {
        Fixture fixture = persistPetWithBinding("all-unbind");
        port.recordPositivePortion(
                fixture.pet.id,
                fixture.account.id,
                CREATOR_PORTION,
                PortionOrigin.CREATOR_PLAN,
                NOW
        );
        fixture.binding.unbind(NOW.plusSeconds(10));

        Optional<PetReferencePortionPort.ResolvedPortion> current =
                port.currentPositivePortion(fixture.pet.id);
        assertTrue(current.isPresent());
        assertEquals(CREATOR_PORTION, current.get().portionSats());
        assertEquals(PortionOrigin.CREATOR_PLAN, current.get().origin());
        Pet pet = Pet.findById(fixture.pet.id);
        assertNull(pet.foodSourceAccount);
        assertEquals(CREATOR_PORTION, pet.lastPositivePortionSats);
    }

    @Test
    @Transactional
    void recordPortionNaoPositivaLancaIllegalArgumentException() {
        Fixture fixture = persistPetWithBinding("non-positive");

        IllegalArgumentException zero = assertThrows(
                IllegalArgumentException.class,
                () -> port.recordPositivePortion(
                        fixture.pet.id,
                        fixture.account.id,
                        0L,
                        PortionOrigin.CREATOR_PLAN,
                        NOW
                )
        );
        assertTrue(zero.getMessage().contains("maior que zero"), zero.getMessage());

        IllegalArgumentException negative = assertThrows(
                IllegalArgumentException.class,
                () -> port.recordPositivePortion(
                        fixture.pet.id,
                        fixture.account.id,
                        -1L,
                        PortionOrigin.CREATOR_PLAN,
                        NOW
                )
        );
        assertTrue(negative.getMessage().contains("maior que zero"), negative.getMessage());
        assertTrue(((Pet) Pet.findById(fixture.pet.id)).awaitingReference);
    }

    @Test
    @Transactional
    void petInexistenteLancaIllegalArgumentException() {
        UUID missing = UUID.randomUUID();

        IllegalArgumentException current = assertThrows(
                IllegalArgumentException.class,
                () -> port.currentPositivePortion(missing)
        );
        assertTrue(current.getMessage().contains("Pet não encontrado"), current.getMessage());

        IllegalArgumentException record = assertThrows(
                IllegalArgumentException.class,
                () -> port.recordPositivePortion(
                        missing,
                        UUID.randomUUID(),
                        CREATOR_PORTION,
                        PortionOrigin.CREATOR_PLAN,
                        NOW
                )
        );
        assertTrue(record.getMessage().contains("Pet não encontrado"), record.getMessage());
    }

    @Test
    @Transactional
    void contaRecemVinculadaNaoApagaLastPositiveNemReescreveSnapshots() {
        Fixture fixture = persistPetWithBinding("new-bind");
        port.recordPositivePortion(
                fixture.pet.id,
                fixture.account.id,
                CREATOR_PORTION,
                PortionOrigin.CREATOR_PLAN,
                NOW
        );
        UUID snapshotId = PetReferencePortion.latestForPet(fixture.pet).orElseThrow().id;
        long snapshotCount = PetReferencePortion.count("pet", fixture.pet);

        Account newcomer = persistAccount("newcomer");
        AccountAddressBinding.create(
                newcomer, fixture.address, false, NOW.plusSeconds(300)).persist();

        Optional<PetReferencePortionPort.ResolvedPortion> current =
                port.currentPositivePortion(fixture.pet.id);

        assertTrue(current.isPresent());
        assertEquals(CREATOR_PORTION, current.get().portionSats());
        assertEquals(fixture.account.id, current.get().sourceAccountId());
        Pet pet = Pet.findById(fixture.pet.id);
        assertEquals(CREATOR_PORTION, pet.lastPositivePortionSats);
        assertEquals(fixture.account.id, pet.foodSourceAccount.id);
        assertEquals(snapshotCount, PetReferencePortion.count("pet", pet));
        List<PetReferencePortion> snapshots = PetReferencePortion.list("pet", pet);
        assertEquals(1, snapshots.size());
        assertEquals(snapshotId, snapshots.get(0).id);
        assertEquals(CREATOR_PORTION, snapshots.get(0).portionSats);
    }

    private static Fixture persistPetWithBinding(String marker) {
        Address address = Address.create(uniqueCanonical(marker), NOW);
        address.persist();
        Account account = persistAccount(marker);
        AccountAddressBinding binding = AccountAddressBinding.create(account, address, true, NOW);
        binding.persist();
        Pet pet = Pet.create(address, account, "Pixel-" + marker, NOW);
        pet.persist();
        return new Fixture(pet, account, address, binding);
    }

    private static Account persistAccount(String marker) {
        Account account = Account.create(
                marker + "-" + UUID.randomUUID() + "@test.com",
                "America/Sao_Paulo",
                "pt-BR",
                NOW
        );
        account.persist();
        return account;
    }

    private static String uniqueCanonical(String marker) {
        return "bcrt1q" + marker.replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private record Fixture(
            Pet pet, Account account, Address address, AccountAddressBinding binding
    ) {
    }
}
