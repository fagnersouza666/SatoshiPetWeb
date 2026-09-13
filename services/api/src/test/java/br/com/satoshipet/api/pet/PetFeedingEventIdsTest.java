package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.pet.engine.ReserveMath;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PetFeedingEventIdsTest {

    @Test
    void appliedPermaneceEstavelQuandoAmountMuda() {
        PetFeeding feeding = feeding(5_000L, hours("6"));
        UUID first = PetFeedingEventIds.of("PET_FEEDING_APPLIED", feeding);
        feeding.amountSats = 10_000L;
        feeding.durationHours = hours("12");
        assertEquals(first, PetFeedingEventIds.of("PET_FEEDING_APPLIED", feeding));
    }

    @Test
    void revisedMudaComAmountEDuration() {
        PetFeeding feeding = feeding(5_000L, hours("6"));
        UUID first = PetFeedingEventIds.of("PET_FEEDING_REVISED", feeding);
        feeding.amountSats = 10_000L;
        feeding.durationHours = hours("12");
        UUID second = PetFeedingEventIds.of("PET_FEEDING_REVISED", feeding);
        assertNotEquals(first, second);
        assertEquals(second, PetFeedingEventIds.of("PET_FEEDING_REVISED", feeding));
    }

    private static PetFeeding feeding(long amountSats, BigDecimal durationHours) {
        Instant now = Instant.parse("2026-09-13T15:00:00Z");
        Address address = Address.create("bcrt1qfeedingeventids000000000000001", now);
        Account account = Account.create("feeding-ids@test.com", "America/Sao_Paulo", "pt-BR", now);
        Pet pet = Pet.create(address, account, "Pixel", now);
        return PetFeeding.create(
                pet,
                UUID.randomUUID(),
                amountSats,
                20_000L,
                durationHours,
                now,
                FeedingStatus.PROVISIONAL,
                FeedingOrigin.LIVE,
                true,
                now
        );
    }

    private static BigDecimal hours(String value) {
        return new BigDecimal(value).setScale(ReserveMath.SCALE);
    }
}
