package br.com.satoshipet.api.outbox;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de domínio persistido no outbox transacional.
 * O {@link OutboxPublisher} lê periodicamente eventos pendentes e os despacha
 * para os {@link OutboxConsumer}s registrados.
 */
@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_events_pending", columnList = "processed_at, created_at")
        }
)
public class OutboxEvent extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    /** Tipo do agregado que originou o evento (ex: "Account", "Pet"). */
    @Column(name = "aggregate_type", nullable = false, length = 100, updatable = false)
    public String aggregateType;

    /** Identificador do agregado (geralmente UUID em string). */
    @Column(name = "aggregate_id", nullable = false, length = 100, updatable = false)
    public String aggregateId;

    /**
     * Tipo do evento segundo o catálogo único (PRD §16.3).
     * Ex: "BITCOIN_TRANSACTION_OBSERVED", "PET_FEEDING_APPLIED".
     */
    @Column(name = "event_type", nullable = false, length = 100, updatable = false)
    public String eventType;

    /** Payload JSON do evento. Nunca expõe campos da denylist (CA-009). */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT", updatable = false)
    public String payload;

    /** Momento em que o evento foi gravado. */
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    /** Preenchido pelo OutboxPublisher ao processar com sucesso. */
    @Column(name = "processed_at")
    public Instant processedAt;

    /** Identificador de correlação para rastreabilidade. */
    @Column(name = "correlation_id", length = 36)
    public String correlationId;

    /** Número de tentativas de publicação realizadas. */
    @Column(name = "retries", nullable = false)
    public int retries;

    protected OutboxEvent() {
        // Construtor exigido pelo Hibernate ORM.
    }

    /** Cria um evento com id gerado e timestamp fornecido. */
    public static OutboxEvent create(
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            Instant createdAt,
            String correlationId
    ) {
        return createWithId(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                eventType,
                payload,
                createdAt,
                correlationId
        );
    }

    /** Cria um evento com id explícito (chave de idempotência do outbox). */
    public static OutboxEvent createWithId(
            UUID id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            Instant createdAt,
            String correlationId
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(createdAt, "createdAt");

        OutboxEvent event = new OutboxEvent();
        event.id = id;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payload = payload;
        event.createdAt = createdAt;
        event.correlationId = correlationId;
        event.retries = 0;
        return event;
    }

    /**
     * Retorna até {@code limit} eventos ainda não processados,
     * ordenados do mais antigo para o mais recente e, em empate,
     * pelo identificador para manter a ordem determinística.
     */
    public static List<OutboxEvent> findPending(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit deve ser positivo");
        }

        return find("processedAt IS NULL ORDER BY createdAt ASC, id ASC")
                .page(0, limit)
                .list();
    }
}
