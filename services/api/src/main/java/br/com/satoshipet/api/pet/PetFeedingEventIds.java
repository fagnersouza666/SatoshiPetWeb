package br.com.satoshipet.api.pet;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Identificadores determinísticos de eventos de alimentação na outbox.
 *
 * <p>{@code PET_FEEDING_APPLIED} e {@code PET_FEEDING_INVALIDATED} usam
 * {@code tipo:feedingId} (retry da mesma TX é no-op). {@code PET_FEEDING_REVISED}
 * inclui amount e duration atuais para que um RBF posterior não seja engolido.</p>
 */
public final class PetFeedingEventIds {

    public static final String PET_FEEDING_REVISED = "PET_FEEDING_REVISED";

    private PetFeedingEventIds() {
    }

    public static UUID of(String eventType, PetFeeding feeding) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(feeding, "feeding");
        Objects.requireNonNull(feeding.id, "feeding.id");
        if (PET_FEEDING_REVISED.equals(eventType)) {
            Objects.requireNonNull(feeding.durationHours, "feeding.durationHours");
            String seed = PET_FEEDING_REVISED + ":" + feeding.id + ":" + feeding.amountSats
                    + ":" + feeding.durationHours.toPlainString();
            return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
        }
        return UUID.nameUUIDFromBytes((eventType + ":" + feeding.id).getBytes(StandardCharsets.UTF_8));
    }
}
