package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.outbox.OutboxEvent;
import br.com.satoshipet.api.pet.engine.EmotionalState;
import br.com.satoshipet.api.pet.engine.ReserveMath;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Estatísticas do pet da conta (PET-17, CC-21).
 *
 * <p>Idade conta desde {@code bornAt} original e não zera no ovo. Tempos por
 * estado vêm da outbox de ciclo de vida + intervalo aberto até agora.</p>
 */
@ApplicationScoped
public class PetStatsService {

    static final String AGGREGATE_PET = "Pet";
    private static final Set<String> LIFECYCLE_TYPES = Set.of(
            "PET_STATE_CHANGED",
            "PET_BORN",
            "PET_REAPPEARED",
            "PET_RETURNED_TO_EGG"
    );
    private static final BigDecimal NANOS_PER_HOUR = new BigDecimal("3600000000000");
    private static final BigDecimal NANOS_PER_SECOND = BigDecimal.valueOf(1_000_000_000L);

    private final ObjectMapper objectMapper;

    @Inject
    public PetStatsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PetStatsResponse stats(Account account) {
        Optional<AccountAddressBinding> binding = AccountAddressBinding.findActivePrimary(account);
        if (binding.isEmpty()) {
            return emptyStats();
        }
        Optional<Pet> petOpt = Pet.findByAddress(binding.get().address);
        if (petOpt.isEmpty()) {
            return emptyStats();
        }
        Pet pet = petOpt.get();
        Instant now = Instant.now();
        String bornAt = pet.bornAt == null ? null : pet.bornAt.toString();
        String ageHours = pet.bornAt == null ? null : hoursBetween(pet.bornAt, now).toPlainString();
        boolean reconstructed = PetFeeding.count(
                "pet = ?1 AND origin = ?2", pet, FeedingOrigin.HISTORICAL_RECONSTRUCTION) > 0;
        boolean observed = PetFeeding.count("pet = ?1 AND origin = ?2", pet, FeedingOrigin.LIVE) > 0;
        return new PetStatsResponse(
                bornAt,
                ageHours,
                reconstructTimeInState(pet, now),
                reconstructed,
                observed
        );
    }

    private TimeInStateHours reconstructTimeInState(Pet pet, Instant now) {
        Map<EmotionalState, BigDecimal> acc = new EnumMap<>(EmotionalState.class);
        for (EmotionalState state : EmotionalState.values()) {
            acc.put(state, BigDecimal.ZERO.setScale(ReserveMath.SCALE));
        }
        List<OutboxEvent> events = OutboxEvent.find(
                "aggregateType = ?1 AND aggregateId = ?2 AND eventType in ?3",
                AGGREGATE_PET,
                pet.id.toString(),
                LIFECYCLE_TYPES
        ).list();
        events.sort(Comparator
                .comparing((OutboxEvent event) -> occurredAt(event))
                .thenComparing(event -> event.id));

        Instant cursor = null;
        EmotionalState current = null;
        boolean tracking = false;
        for (OutboxEvent event : events) {
            Instant at = occurredAt(event);
            if (tracking && current != null && cursor != null) {
                acc.merge(current, hoursBetween(cursor, at), BigDecimal::add);
            }
            JsonNode payload = readPayload(event.payload);
            if ("PET_RETURNED_TO_EGG".equals(event.eventType)) {
                tracking = false;
                current = null;
                cursor = at;
                continue;
            }
            boolean creature = payload.hasNonNull("presentation")
                    && "CREATURE".equals(payload.get("presentation").asText());
            EmotionalState next = parseState(payload);
            if (creature && next != null) {
                tracking = true;
                current = next;
                cursor = at;
            } else {
                tracking = false;
                current = null;
                cursor = at;
            }
        }
        Instant openEnd = now;
        if (pet.lastEvaluatedAt != null && pet.lastEvaluatedAt.isAfter(openEnd)) {
            openEnd = pet.lastEvaluatedAt;
        }
        if (tracking && current != null && cursor != null) {
            acc.merge(current, hoursBetween(cursor, openEnd), BigDecimal::add);
        }
        return new TimeInStateHours(
                acc.get(EmotionalState.ALIMENTADO).toPlainString(),
                acc.get(EmotionalState.PENSANDO).toPlainString(),
                acc.get(EmotionalState.CHATEADO).toPlainString(),
                acc.get(EmotionalState.FAMINTO).toPlainString(),
                acc.get(EmotionalState.CRITICO).toPlainString(),
                acc.get(EmotionalState.HIBERNANDO).toPlainString()
        );
    }

    private Instant occurredAt(OutboxEvent event) {
        JsonNode payload = readPayload(event.payload);
        if (payload.hasNonNull("occurredAt")) {
            try {
                return Instant.parse(payload.get("occurredAt").asText());
            } catch (RuntimeException ignored) {
                return event.createdAt;
            }
        }
        return event.createdAt;
    }

    private static EmotionalState parseState(JsonNode payload) {
        if (payload == null || !payload.hasNonNull("emotionalState")) {
            return null;
        }
        try {
            return EmotionalState.valueOf(payload.get("emotionalState").asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static PetStatsResponse emptyStats() {
        return new PetStatsResponse(null, null, TimeInStateHours.zeros(), false, false);
    }

    static BigDecimal hoursBetween(Instant start, Instant end) {
        Duration elapsed = Duration.between(start, end);
        if (elapsed.isNegative() || elapsed.isZero()) {
            return BigDecimal.ZERO.setScale(ReserveMath.SCALE);
        }
        BigDecimal nanos = BigDecimal.valueOf(elapsed.getSeconds())
                .multiply(NANOS_PER_SECOND)
                .add(BigDecimal.valueOf(elapsed.getNano()));
        return nanos.divide(NANOS_PER_HOUR, ReserveMath.SCALE, RoundingMode.DOWN);
    }

    private JsonNode readPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }
}
