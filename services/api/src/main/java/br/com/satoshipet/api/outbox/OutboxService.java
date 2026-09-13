package br.com.satoshipet.api.outbox;

import br.com.satoshipet.api.events.DomainEventType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Grava eventos no outbox transacional de forma idempotente.
 *
 * <p>O método {@link #save} deve ser chamado dentro da mesma transação que
 * altera o estado do agregado, garantindo que estado e evento sejam persistidos
 * atomicamente (padrão outbox transacional).</p>
 */
@ApplicationScoped
public class OutboxService {

    /**
     * Persiste um evento no outbox.
     * É idempotente: se um evento com o mesmo {@code id} já existir no banco,
     * a chamada não tem efeito.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public void save(
            UUID id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            String correlationId
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(payload, "payload");

        if (OutboxEvent.findById(id) != null) {
            return;
        }

        OutboxEvent event = OutboxEvent.createWithId(
                id,
                aggregateType,
                aggregateId,
                eventType,
                payload,
                Instant.now(),
                correlationId
        );
        event.persist();
    }

    /**
     * Persiste um evento usando o tipo oficial do catálogo da fundação.
     *
     * <p>A sobrecarga textual permanece para compatibilidade com integrações
     * que ainda estão migrando e para eventos exclusivos de teste.</p>
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public void save(
            UUID id,
            String aggregateType,
            String aggregateId,
            DomainEventType eventType,
            String payload,
            String correlationId
    ) {
        Objects.requireNonNull(eventType, "eventType");
        save(id, aggregateType, aggregateId, eventType.value(), payload, correlationId);
    }
}
