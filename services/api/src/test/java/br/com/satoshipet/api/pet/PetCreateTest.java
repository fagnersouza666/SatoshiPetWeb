package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.pet.engine.EmotionalState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Garante o contrato de inicialização de {@link Pet#create} (fonte alimentar CC-05). */
class PetCreateTest {

    @Test
    void defineFonteAlimentarComoContaCriadoraECamposObrigatoriosDoMotor() {
        Instant now = Instant.parse("2026-09-13T12:00:00Z");
        Address address = Address.create("bcrt1qpetcreate0000000000000000000001", now);
        Account creator = Account.create(
                "criador@test.com", "America/Sao_Paulo", "pt-BR", now);

        Pet pet = Pet.create(address, creator, "Pixel", now);

        assertSame(creator, pet.foodSourceAccount);
        assertEquals(0, pet.reserveHours.compareTo(BigDecimal.ZERO));
        assertEquals(10, pet.reserveHours.scale());
        assertEquals(now, pet.lastEvaluatedAt);
        assertEquals(EmotionalState.ALIMENTADO, pet.emotionalState);
        assertEquals(PetPresentation.EGG, pet.presentation);
        assertTrue(pet.awaitingReference);
        assertEquals(ArtworkStatus.NONE, pet.artworkStatus);
        assertNull(pet.lastPositivePortionSats);
        assertNull(pet.lastPositivePortionOrigin);
        assertNull(pet.reserveDepletedAt);
        assertNull(pet.bornAt);
        assertNull(pet.zeroBalanceSince);
        assertNull(pet.lastReturnedToEggAt);
        assertNull(pet.lastReappearedAt);
    }
}
