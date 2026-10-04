package br.com.satoshipet.api.realtime;

import br.com.satoshipet.api.outbox.OutboxEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Projeção durável compartilhada entre réplicas, com replay limitado por consulta. */
@ApplicationScoped
public class RealtimeEventCursorService {
    static final int REPLAY_WINDOW_SIZE = 200;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    @Inject
    public RealtimeEventCursorService(EntityManager entityManager, ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Aplica uma identidade de outbox uma única vez na mesma transação do consumidor.
     * A trava do relógio precede qualquer trava por evento e dura até commit/rollback.
     * Assim um cursor maior não fica visível antes de um cursor menor ainda pendente.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public String record(OutboxEvent event, String canonical) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(canonical, "canonical");
        entityManager.createNativeQuery("SELECT id FROM realtime_cursor_clock WHERE id = 1 FOR UPDATE")
                .getSingleResult();
        OutboxEvent persisted = entityManager.find(OutboxEvent.class, event.id, LockModeType.PESSIMISTIC_WRITE);
        if (persisted == null) throw new IllegalArgumentException("Evento precisa existir na outbox antes da projeção");
        List<Long> existing = entityManager.createQuery(
                        "SELECT r.cursor FROM RealtimeEventReceipt r WHERE r.event.id = :eventId", Long.class)
                .setParameter("eventId", event.id).getResultList();
        if (!existing.isEmpty()) return existing.getFirst().toString();
        RealtimeEventReceipt receipt = new RealtimeEventReceipt();
        receipt.event = persisted;
        receipt.canonical = canonical;
        entityManager.persist(receipt);
        entityManager.flush();
        return receipt.cursor.toString();
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public String currentCursor(String canonical) {
        Long cursor = entityManager.createQuery(
                        "SELECT MAX(r.cursor) FROM RealtimeEventReceipt r WHERE r.canonical = :canonical", Long.class)
                .setParameter("canonical", canonical).getSingleResult();
        return cursor == null ? "0" : cursor.toString();
    }

    /**
     * Carrega no máximo 200 eventos recentes, sem manter cópia histórica no heap.
     * Cursor fora da janela, inválido ou adiantado exige snapshot do estado atual.
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public Replay replayAfter(String canonical, String requestedCursor) {
        List<RealtimeEventReceipt> latest = entityManager.createQuery(
                        "SELECT r FROM RealtimeEventReceipt r JOIN FETCH r.event "
                                + "WHERE r.canonical = :canonical ORDER BY r.cursor DESC", RealtimeEventReceipt.class)
                .setParameter("canonical", canonical).setMaxResults(REPLAY_WINDOW_SIZE).getResultList();
        String current = latest.isEmpty() ? "0" : latest.getFirst().cursor.toString();
        long after;
        try {
            after = Long.parseLong(requestedCursor);
            if (after < 0) return new Replay(current, List.of(), true);
        } catch (NumberFormatException exception) {
            return new Replay(current, List.of(), true);
        }
        long maximum = Long.parseLong(current);
        if (after > maximum) return new Replay(current, List.of(), true);
        if (latest.isEmpty()) return new Replay(current, List.of(), false);
        long oldest = latest.getLast().cursor;
        if (after < oldest && (latest.size() == REPLAY_WINDOW_SIZE || after != 0)) {
            return new Replay(current, List.of(), true);
        }
        List<StoredEvent> events = new ArrayList<>();
        for (RealtimeEventReceipt receipt : latest) {
            if (receipt.cursor <= after) continue;
            try {
                events.add(new StoredEvent(receipt.cursor.toString(), receipt.event.eventType,
                        objectMapper.readTree(receipt.event.payload)));
            } catch (JsonProcessingException exception) {
                // Um payload ilegível não pode ser pulado como se tivesse sido aplicado.
                return new Replay(current, List.of(), true);
            }
        }
        Collections.reverse(events);
        return new Replay(current, List.copyOf(events), false);
    }

    public record Replay(String currentCursor, List<StoredEvent> events, boolean snapshotRequired) {}
    public record StoredEvent(String cursor, String eventType, Object data) {}
}
