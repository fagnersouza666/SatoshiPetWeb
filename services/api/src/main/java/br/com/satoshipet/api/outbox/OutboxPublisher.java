package br.com.satoshipet.api.outbox;

import br.com.satoshipet.api.platform.CorrelationIdContext;
import br.com.satoshipet.api.job.JobLockService;
import io.quarkus.arc.All;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Publica eventos pendentes do outbox transacional para os consumidores registrados.
 *
 * <p>Usa {@link JobLockService} para garantir que apenas uma instância processe
 * eventos do mesmo agregado ao mesmo tempo. Agregados diferentes podem ser
 * processados em paralelo por réplicas distintas.</p>
 */
@ApplicationScoped
public class OutboxPublisher {

    private static final Logger LOG = Logger.getLogger(OutboxPublisher.class);

    /** Nome do job de lock distribuído. */
    static final String JOB_NAME = "outbox-publisher";

    /** Prefixo das travas distribuídas por agregado do outbox. */
    static final String DOMAIN_LOCK_PREFIX = "outbox-domain:";

    /** Limite da coluna {@code job_locks.job_name}. */
    private static final int MAX_LOCK_NAME_LENGTH = 100;

    /** TTL da trava por ciclo de poll. */
    static final Duration LOCK_TTL = Duration.ofSeconds(30);

    /** Máximo de eventos processados por ciclo (evita bloquear o thread por muito tempo). */
    static final int BATCH_SIZE = 50;

    private final JobLockService jobLockService;
    private final List<OutboxConsumer> consumers;

    public OutboxPublisher(JobLockService jobLockService, @All List<OutboxConsumer> consumers) {
        this.jobLockService = jobLockService;
        this.consumers = consumers;
    }

    /**
     * Varre eventos pendentes a cada 5 s.
     * Cada evento tenta adquirir a trava do seu agregado. Eventos cujo agregado
     * esteja sendo processado por outra réplica ficam pendentes para o próximo
     * ciclo.
     */
    @Scheduled(every = "5s", identity = JOB_NAME)
    public void poll() {
        processNextBatch();
    }

    void processNextBatch() {
        List<PendingEvent> pending = QuarkusTransaction.requiringNew().call(() ->
                OutboxEvent.findPending(BATCH_SIZE).stream()
                        .map(event -> new PendingEvent(event.id, domainLockName(event))).toList());
        if (pending.isEmpty()) return;

        String ownerId = UUID.randomUUID().toString();
        Set<String> acquiredLocks = new LinkedHashSet<>();
        Set<String> unavailableLocks = new LinkedHashSet<>();
        try {
            for (PendingEvent event : pending) {
                processEvent(event, ownerId, acquiredLocks, unavailableLocks);
            }
        } finally {
            // Cada unidade já confirmou/reverteu antes de liberar sua concessão.
            for (String lockName : acquiredLocks) jobLockService.release(lockName, ownerId);
        }
    }

    /** Relê o evento dentro da mesma transação que protege sua concessão. */
    private void processEvent(PendingEvent event, String ownerId,
                              Set<String> acquiredLocks, Set<String> unavailableLocks) {
        String lockName = event.lockName();
        if (unavailableLocks.contains(lockName)) return;
        if (!acquiredLocks.contains(lockName)
                && !jobLockService.acquire(lockName, ownerId, LOCK_TTL)) {
            unavailableLocks.add(lockName);
            return;
        }
        acquiredLocks.add(lockName);
        try {
            boolean[] processed = {true};
            boolean owned = jobLockService.runWhileOwned(lockName, ownerId, LOCK_TTL, () -> {
                OutboxEvent current = OutboxEvent.findById(event.id());
                if (current == null || current.processedAt != null) return;
                dispatchEvent(current);
                processed[0] = current.processedAt != null;
            });
            // Não ultrapassa um evento falho nem continua após perder a concessão.
            if (!owned || !processed[0]) unavailableLocks.add(lockName);
        } catch (RuntimeException failure) {
            unavailableLocks.add(lockName);
            LOG.errorf(failure, "Falha na unidade do outbox id=%s; agregado aguardará próximo ciclo", event.id());
        }
    }

    private record PendingEvent(UUID id, String lockName) {}

    private void dispatchEvent(OutboxEvent event) {
        try (CorrelationIdContext.Scope ignored = CorrelationIdContext.open(event.correlationId)) {
            dispatchEventWithContext(event);
        }
    }

    private void dispatchEventWithContext(OutboxEvent event) {
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

    /**
     * Gera a chave estável da trava para o agregado do evento.
     *
     * <p>Os valores usuais permanecem legíveis para facilitar operação. Quando
     * a combinação ultrapassa o limite do banco, um digest SHA-256 mantém a
     * chave estável e dentro de {@code job_locks.job_name}.</p>
     */
    static String domainLockName(OutboxEvent event) {
        Objects.requireNonNull(event, "event");
        return domainLockName(event.aggregateType, event.aggregateId);
    }

    static String domainLockName(String aggregateType, String aggregateId) {
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");

        String readableName = DOMAIN_LOCK_PREFIX + aggregateType + ":" + aggregateId;
        if (readableName.length() <= MAX_LOCK_NAME_LENGTH) {
            return readableName;
        }

        byte[] input = (aggregateType + "\u0000" + aggregateId).getBytes(StandardCharsets.UTF_8);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
            return DOMAIN_LOCK_PREFIX + "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }
}
