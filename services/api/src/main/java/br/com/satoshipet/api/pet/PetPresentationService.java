package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.outbox.OutboxEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Fila de apresentação por conta (PET-15, CC-15, CA-034, CA-035).
 *
 * <p>Pular animações só avança o {@link PresentationCursor}; não altera reserva,
 * estado emocional nem apresentação. Visitante anônimo não persiste cursor.</p>
 */
@ApplicationScoped
public class PetPresentationService {

    static final String AGGREGATE_PET = "Pet";
    static final String PET_FEEDING_APPLIED = "PET_FEEDING_APPLIED";
    static final String PET_FEEDING_REVISED = "PET_FEEDING_REVISED";
    static final String PET_BORN = "PET_BORN";
    static final String PET_REAPPEARED = "PET_REAPPEARED";

    private static final Set<String> QUEUE_EVENT_TYPES = Set.of(
            PET_FEEDING_APPLIED,
            PET_FEEDING_REVISED,
            PET_BORN,
            PET_REAPPEARED
    );
    private static final Set<String> FEEDING_EVENT_TYPES = Set.of(PET_FEEDING_APPLIED, PET_FEEDING_REVISED);

    private final ObjectMapper objectMapper;

    @Inject
    public PetPresentationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PresentationQueueResponse presentationQueue(Account account) {
        List<OutboxEvent> eligible = eligibleEvents(account);
        Optional<OutboxEvent> watermark = cursorWatermark(account);
        List<PresentationQueueItem> items = eligible.stream()
                .filter(event -> watermark.map(cursor -> afterCursor(event, cursor)).orElse(true))
                .map(this::toItem)
                .toList();
        return new PresentationQueueResponse(items);
    }

    @Transactional
    public SkipPresentationResponse skip(Account account) {
        List<OutboxEvent> eligible = eligibleEvents(account);
        if (eligible.isEmpty()) {
            return new SkipPresentationResponse(true);
        }
        OutboxEvent last = eligible.getLast();
        Instant now = Instant.now();
        PresentationCursor cursor = PresentationCursor.findByAccount(account).orElseGet(() -> {
            PresentationCursor created = PresentationCursor.create(account, now);
            created.persist();
            return created;
        });
        cursor.lastPresentedEventId = last.id;
        cursor.updatedAt = now;
        return new SkipPresentationResponse(true);
    }

    private List<OutboxEvent> eligibleEvents(Account account) {
        Optional<AccountAddressBinding> binding = AccountAddressBinding.findActivePrimary(account);
        if (binding.isEmpty()) {
            return List.of();
        }
        Optional<Pet> pet = Pet.findByAddress(binding.get().address);
        if (pet.isEmpty()) {
            return List.of();
        }
        List<OutboxEvent> candidates = OutboxEvent.find(
                "aggregateType = ?1 AND aggregateId = ?2 AND createdAt >= ?3 "
                        + "AND eventType in ?4 ORDER BY createdAt ASC, id ASC",
                AGGREGATE_PET,
                pet.get().id.toString(),
                binding.get().boundAt,
                QUEUE_EVENT_TYPES
        ).list();
        List<PetFeeding> feedings = PetFeeding.listByPet(pet.get());
        return candidates.stream()
                .filter(event -> isPresentable(event, feedings))
                .toList();
    }

    private static boolean isPresentable(OutboxEvent event, List<PetFeeding> feedings) {
        if (!QUEUE_EVENT_TYPES.contains(event.eventType)) {
            return false;
        }
        if (!FEEDING_EVENT_TYPES.contains(event.eventType)) {
            return true;
        }
        for (PetFeeding feeding : feedings) {
            if (feeding.origin != FeedingOrigin.LIVE
                    || !feeding.presentable
                    || feeding.status == FeedingStatus.INVALIDATED) {
                continue;
            }
            if (PetFeedingEventIds.of(event.eventType, feeding).equals(event.id)) {
                return true;
            }
        }
        return false;
    }

    private Optional<OutboxEvent> cursorWatermark(Account account) {
        Optional<PresentationCursor> cursor = PresentationCursor.findByAccount(account);
        if (cursor.isEmpty() || cursor.get().lastPresentedEventId == null) {
            return Optional.empty();
        }
        OutboxEvent event = OutboxEvent.findById(cursor.get().lastPresentedEventId);
        return Optional.ofNullable(event);
    }

    private static boolean afterCursor(OutboxEvent event, OutboxEvent cursorEvent) {
        int byTime = event.createdAt.compareTo(cursorEvent.createdAt);
        if (byTime != 0) {
            return byTime > 0;
        }
        return event.id.compareTo(cursorEvent.id) > 0;
    }

    private PresentationQueueItem toItem(OutboxEvent event) {
        JsonNode payload = readPayload(event.payload);
        String occurredAt = textOr(payload, "occurredAt", event.createdAt.toString());
        Long amountSats = longOrNull(payload, "amountSats");
        return new PresentationQueueItem(event.id.toString(), event.eventType, occurredAt, amountSats);
    }

    private JsonNode readPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }

    private static String textOr(JsonNode payload, String field, String fallback) {
        if (payload != null && payload.hasNonNull(field)) {
            return payload.get(field).asText();
        }
        return fallback;
    }

    private static Long longOrNull(JsonNode payload, String field) {
        if (payload != null && payload.hasNonNull(field)) {
            return payload.get(field).asLong();
        }
        return null;
    }
}
