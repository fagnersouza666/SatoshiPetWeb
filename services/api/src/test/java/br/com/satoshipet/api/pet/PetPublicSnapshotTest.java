package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.pet.engine.EmotionalState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapper compartilhado do bloco público do pet (CA-009). Sem persistência.
 */
class PetPublicSnapshotTest {

    @Test
    void ovoOmitePetStateERotulaAguardandoReferencia() {
        Pet pet = novoOvo("Pixel");

        PetPublicSnapshot snapshot = PetPublicSnapshot.from(pet, 0L);

        assertEquals("Pixel", snapshot.petName());
        assertEquals("EGG", snapshot.presentation());
        assertNull(snapshot.petState());
        assertEquals(pet.reserveHours.toPlainString(), snapshot.reserveHours());
        assertTrue(snapshot.awaitingReference());
        assertFalse(snapshot.pendingMovesEgg());
        assertEquals("Aguardando referência do plano", snapshot.operationalLabel());
    }

    @Test
    void awaitingReferenceTemPrecedenciaSobreRecebimentoPendente() {
        Pet pet = novoOvo("Pixel");

        PetPublicSnapshot snapshot = PetPublicSnapshot.from(pet, 5_000L);

        assertTrue(snapshot.pendingMovesEgg());
        assertEquals("Aguardando referência do plano", snapshot.operationalLabel());
    }

    @Test
    void ovoComPendenteSemReferenciaRotulaRecebimentoPendente() {
        Pet pet = novoOvo("Pixel");
        pet.awaitingReference = false;

        PetPublicSnapshot snapshot = PetPublicSnapshot.from(pet, 1_000L);

        assertNull(snapshot.petState());
        assertTrue(snapshot.pendingMovesEgg());
        assertEquals("Recebimento pendente", snapshot.operationalLabel());
    }

    @Test
    void ovoComBornAtEArteNaoAprovadaRotulaPreparandoNascimento() {
        Pet pet = novoOvo("Pixel");
        pet.awaitingReference = false;
        pet.bornAt = Instant.parse("2026-09-12T12:00:00Z");
        pet.artworkStatus = ArtworkStatus.PENDING;

        PetPublicSnapshot snapshot = PetPublicSnapshot.from(pet, 0L);

        assertFalse(snapshot.pendingMovesEgg());
        assertEquals("Preparando nascimento", snapshot.operationalLabel());
    }

    @Test
    void ovoComArteAprovadaNaoRotulaPreparandoNascimento() {
        Pet pet = novoOvo("Pixel");
        pet.awaitingReference = false;
        pet.bornAt = Instant.parse("2026-09-12T12:00:00Z");
        pet.artworkStatus = ArtworkStatus.APPROVED;

        PetPublicSnapshot snapshot = PetPublicSnapshot.from(pet, 0L);

        assertNull(snapshot.operationalLabel());
    }

    @Test
    void criaturaExpoeEstadoEmocionalENaoMoveOvo() {
        Pet pet = novoOvo("Pixel");
        pet.awaitingReference = false;
        pet.presentation = PetPresentation.CREATURE;
        pet.emotionalState = EmotionalState.PENSANDO;
        pet.reserveHours = new BigDecimal("6.5000000000");

        PetPublicSnapshot snapshot = PetPublicSnapshot.from(pet, 9_000L);

        assertEquals("CREATURE", snapshot.presentation());
        assertEquals("PENSANDO", snapshot.petState());
        assertEquals("6.5000000000", snapshot.reserveHours());
        assertFalse(snapshot.pendingMovesEgg());
        assertNull(snapshot.operationalLabel());
    }

    @Test
    void semPetTodosOsCamposFicamNulos() {
        PetPublicSnapshot snapshot = PetPublicSnapshot.empty();

        assertNull(snapshot.petName());
        assertNull(snapshot.presentation());
        assertNull(snapshot.petState());
        assertNull(snapshot.reserveHours());
        assertNull(snapshot.awaitingReference());
        assertNull(snapshot.pendingMovesEgg());
        assertNull(snapshot.operationalLabel());
    }

    private static Pet novoOvo(String name) {
        Instant now = Instant.parse("2026-09-13T12:00:00Z");
        Address address = Address.create("bcrt1qsnapshot0000000000000000000001", now);
        Account creator = Account.create("snapshot@test.com", "America/Sao_Paulo", "pt-BR", now);
        return Pet.create(address, creator, name, now);
    }
}
