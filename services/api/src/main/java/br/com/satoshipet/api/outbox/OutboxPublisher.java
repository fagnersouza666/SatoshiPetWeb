package br.com.satoshipet.api.outbox;

import br.com.satoshipet.api.job.JobLockService;
import io.quarkus.arc.All;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Publica eventos pendentes do outbox transacional para os consumidores registrados.
 *
 * <p>Usa {@link JobLockService} para garantir que apenas uma instância da aplicação
 * processe os eventos ao mesmo tempo em ambientes multi-réplica.</p>
 */
@ApplicationScoped
public class OutboxPublisher {

    private static final Logger LOG = Logger.getLogger(OutboxPublisher.class);

    /** Nome do job de lock distribuído. */
    static final String JOB_NAME = "outbox-publisher";

    /** TTL da trava por ciclo de poll. */
    static final Duration LOCK_TTL = Duration.ofSeconds(30);

    /** Máximo de eventos processados por ciclo (evita bloquear o thread por muito tempo). */
    static final int BATCH_SIZE = 50;

    private final JobLockService jobLockService;
    private final List<OutboxConsumer> consumers;

    /** Owner único por instância, gerado na inicialização. */
    private final String ownerId = UUID.randomUUID().toString();

    public OutboxPublisher(JobLockService jobLockService, @All List<OutboxConsumer> consumers) {
        this.jobLockService = jobLockService;
        this.consumers = consumers;
    }

    /**
     * Varre eventos pendentes a cada 5 s.
     * Só executa se conseguir adquirir a trava distribuída.
     */
    @Scheduled(every = "5s", identity = JOB_NAME)
    public void poll() {
        if (!jobLockService.acquire(JOB_NAME, ownerId, LOCK_TTL)) {
            return; // outra instância está processando
        }
        try {
            processNextBatch();
        } finally {
            jobLockService.release(JOB_NAME, ownerId);
        }
    }

    @Transactional
    void processNextBatch() {
        List<OutboxEvent> pending = OutboxEvent.findPending(BATCH_SIZE);
        if (pending.isEmpty()) {
            return;
        }

        LOG.debugf("Processando %d eventos do outbox.", pending.size());

        for (OutboxEvent event : pending) {
            dispatchEvent(event);
        }
    }

    private void dispatchEvent(OutboxEvent event) {
        List<OutboxConsumer> matched = consumers.stream()
                .filter(c -> c.supports(event.eventType))
                .toList();

        if (matched.isEmpty()) {
            // Evento sem consumidor — marca como processado para não bloquear o outbox.
            LOG.warnf("Nenhum consumidor para eventType=%s id=%s", event.eventType, event.id);
            markProcessed(event);
            return;
        }

        boolean allSucceeded = true;
        for (OutboxConsumer consumer : matched) {
            try {
                consumer.consume(event);
            } catch (Exception e) {
                allSucceeded = false;
                event.retries++;
                LOG.errorf(e, "Falha ao consumir evento id=%s type=%s tentativa=%d",
                        event.id, event.eventType, event.retries);
            }
        }

        if (allSucceeded) {
            markProcessed(event);
        }
    }

    private void markProcessed(OutboxEvent event) {
        event.processedAt = Instant.now();
    }
}
