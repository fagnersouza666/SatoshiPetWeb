package br.com.satoshipet.api.pet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AccountPetResponseTest {

    @Test
    void copiaBlocoCompartilhadoEAnexaFilaEStats() {
        PetPublicSnapshot snapshot = new PetPublicSnapshot(
                "Pixel",
                "EGG",
                null,
                "0.0000000000",
                true,
                false,
                "Aguardando referência do plano"
        );
        PresentationQueueResponse queue = new PresentationQueueResponse(java.util.List.of());
        PetStatsResponse stats = new PetStatsResponse(null, null, TimeInStateHours.zeros(), false, false);

        AccountPetResponse response = AccountPetResponse.of(snapshot, queue, stats);

        assertEquals("Pixel", response.petName());
        assertEquals("EGG", response.presentation());
        assertNull(response.petState());
        assertEquals("0.0000000000", response.reserveHours());
        assertEquals(true, response.awaitingReference());
        assertEquals(false, response.pendingMovesEgg());
        assertEquals("Aguardando referência do plano", response.operationalLabel());
        assertSame(queue, response.presentationQueue());
        assertSame(stats, response.stats());
    }
}
