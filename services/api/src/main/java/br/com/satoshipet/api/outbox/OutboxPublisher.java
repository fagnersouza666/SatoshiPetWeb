package br.com.satoshipet.api.outbox;

import br.com.satoshipet.api.job.JobLockService;
import io.quarkus.arc.All;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionSynchronizationRegistry;
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

    /** Permite liberar a trava somente depois do commit da transação do lote. */
    @Inject
    TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    /** Owner único por instância, gerado na inicialização. */
    private final String ownerId = UUID.randomUUID().toString();

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
    @Transactional
    public void poll() {
        processNextBatch();
    }

    @Transactional
    void processNextBatch() {
        List<OutboxEvent> pending = OutboxEvent.findPending(BATCH_SIZE);
        if (pending.isEmpty()) {
            return;
        }

        LOG.debugf("Processando %d eventos do outbox.", pending.size());

        Set<String> acquiredLocks = new LinkedHashSet<>();
        Set<String> unavailableLocks = new LinkedHashSet<>();
        try {
            for (OutboxEvent event : pending) {
                processEvent(event, acquiredLocks, unavailableLocks);
            }
        } finally {
            releaseAfterTransaction(acquiredLocks);
        }
    }

    /**
     * Processa um evento somente enquanto sua trava de agregado estiver detida
     * por esta instância. A trava é compartilhada por todos os eventos do mesmo
     * agregado encontrados neste lote.
     */
    private void processEvent(
            OutboxEvent event,
            Set<String> acquiredLocks,
            Set<String> unavailableLocks
    ) {
        String lockName = domainLockName(event);
        if (unavailableLocks.contains(lockName)) {
            return;
        }

        if (!acquiredLocks.contains(lockName)
                && !jobLockService.acquire(lockName, ownerId, LOCK_TTL)) {
            unavailableLocks.add(lockName);
            LOG.debugf("Lock do agregado detido por outra instância: lock=%s event=%s",
                    lockName, event.id);
            return;
        }

        acquiredLocks.add(lockName);
        dispatchEvent(event);
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

    /**
     * Adia a liberação para depois do fim da transação que marcou os eventos.
     * Sem isso, uma réplica poderia readquirir a trava enquanto
     * {@code processed_at} ainda não foi confirmado no banco.
     */
    private void releaseAfterTransaction(Set<String> lockNames) {
        if (lockNames.isEmpty()) {
            return;
        }

        TransactionSynchronizationRegistry registry = transactionSynchronizationRegistry;
        if (registry != null) {
            int status = registry.getTransactionStatus();
            if (status == Status.STATUS_ACTIVE || status == Status.STATUS_MARKED_ROLLBACK) {
                List<String> locksToRelease = List.copyOf(lockNames);
                try {
                    registry.registerInterposedSynchronization(new jakarta.transaction.Synchronization() {
                        @Override
                        public void beforeCompletion() {
                            // Nada a fazer antes do commit.
                        }

                        @Override
                        public void afterCompletion(int completionStatus) {
                            releaseLocks(locksToRelease);
                        }
                    });
                    return;
                } catch (IllegalStateException e) {
                    LOG.warnf(e, "Não foi possível registrar liberação pós-transação das travas do outbox");
                }
            }
        }

        // Fallback para invocações fora do CDI/transação, útil para testes e shutdown.
        releaseLocks(lockNames);
    }

    private void releaseLocks(Iterable<String> lockNames) {
        for (String lockName : lockNames) {
            jobLockService.release(lockName, ownerId);
        }
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
