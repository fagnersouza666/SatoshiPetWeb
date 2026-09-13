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
        save(OutboxEvent.createWithId(
                id,
                aggregateType,
                aggregateId,
                eventType,
                payload,
                Instant.now(),
                correlationId
        ));
    }

    /**
     * Persiste um evento já construído na transação corrente.
     *
     * <p>O evento e a alteração do agregado devem ser enviados à mesma
     * transação pelo serviço de aplicação. A exigência {@code MANDATORY}
     * impede que uma chamada acidental crie uma transação independente e
     * deixe o estado do agregado sem o respectivo evento.</p>
     *
     * <p>A consulta pela chave primária torna a operação idempotente para
     * reprocessamentos que reutilizam o mesmo ID lógico. O registro existente
     * nunca é atualizado por uma retransmissão.</p>
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public void save(OutboxEvent event) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(event.id, "event.id");
        Objects.requireNonNull(event.aggregateType, "event.aggregateType");
        Objects.requireNonNull(event.aggregateId, "event.aggregateId");
        Objects.requireNonNull(event.eventType, "event.eventType");
        Objects.requireNonNull(event.payload, "event.payload");
        Objects.requireNonNull(event.createdAt, "event.createdAt");

        if (OutboxEvent.findById(event.id) == null) {
            event.persist();
        }
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
