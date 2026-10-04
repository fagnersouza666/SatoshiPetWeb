package br.com.satoshipet.api.job;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Serviço de travas distribuídas baseado em banco de dados.
 *
 * <p>Garante execução exclusiva de jobs agendados em ambientes multi-réplica.
 * Usa um upsert condicional atômico dentro de {@code REQUIRES_NEW}, de modo que
 * a contenção entre instâncias não polua a transação chamadora.</p>
 */
@ApplicationScoped
public class JobLockService {

    private static final Logger LOG = Logger.getLogger(JobLockService.class);

    private final EntityManager em;
    private final JobLockAcquisition acquisition;

    @Inject
    public JobLockService(EntityManager em, JobLockAcquisition acquisition) {
        this.em = em;
        this.acquisition = acquisition;
    }

    /**
     * Tenta adquirir a trava para o job informado.
     *
     * <p>A persistência roda numa transação independente ({@code REQUIRES_NEW})
     * para isolar a contenção sem afetar a transação chamadora.</p>
     *
     * @param jobName nome único do job
     * @param ownerId identificador da instância/thread que requisita a trava
     * @param ttl     tempo de vida da trava; expirado, qualquer instância pode assumir
     * @return {@code true} se a trava foi adquirida; {@code false} se outra instância
     *     ainda detém uma trava ativa para o mesmo job
     */
    public boolean acquire(String jobName, String ownerId, Duration ttl) {
        Objects.requireNonNull(jobName, "jobName");
        Objects.requireNonNull(ownerId, "ownerId");
        requirePositiveTtl(ttl);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        try {
            int affected = acquisition.execute(jobName, ownerId, now, expiresAt);
            if (affected == 1) {
                LOG.debugf("Lock adquirido: job=%s owner=%s", jobName, ownerId);
                return true;
            }
        } catch (PersistenceException e) {
            // O adaptador H2 pode sinalizar uma disputa de inserção como colisão
            // de chave; a transação própria já foi revertida pelo interceptor.
        }

        LOG.debugf("Lock já detido por outro owner: job=%s", jobName);
        return false;
    }

    /**
     * Renova o TTL de um lock já detido pelo {@code ownerId}.
     * Não tem efeito se o lock não pertencer a este owner.
     *
     * @return {@code true} se o lock foi renovado
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean renew(String jobName, String ownerId, Duration ttl) {
        Objects.requireNonNull(jobName, "jobName");
        Objects.requireNonNull(ownerId, "ownerId");
        requirePositiveTtl(ttl);

        Instant now = Instant.now();
        int updated = em.createNativeQuery(
                "UPDATE job_locks SET acquired_at = :now, expires_at = :exp "
                + "WHERE job_name = :name AND owner_id = :owner AND expires_at > :now")
                .setParameter("now", now)
                .setParameter("exp", now.plus(ttl))
                .setParameter("name", jobName)
                .setParameter("owner", ownerId)
                .executeUpdate();

        return updated == 1;
    }

    /**
     * Executa uma unidade somente enquanto esta invocação é dona da concessão.
     * A trava de linha permanece até o commit dos efeitos e da renovação: passar
     * o TTL durante o trabalho não autoriza outra réplica a executá-lo em paralelo.
     * O trabalho deve participar desta transação (REQUIRED), sem abrir REQUIRES_NEW.
     *
     * @return false se a concessão expirou, desapareceu ou mudou de dono; nesse
     * caso o chamador deve interromper o ciclo, sem executar o próximo item.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean runWhileOwned(String jobName, String ownerId, Duration ttl, Runnable work) {
        Objects.requireNonNull(jobName, "jobName");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(work, "work");
        requirePositiveTtl(ttl);

        JobLock lock = em.find(JobLock.class, jobName, LockModeType.PESSIMISTIC_WRITE);
        if (lock == null) return false;
        // Releitura sob a trava, inclusive se a aquisição precisou aguardar outra TX.
        em.refresh(lock, LockModeType.PESSIMISTIC_WRITE);
        Instant now = Instant.now();
        if (!ownerId.equals(lock.ownerId) || !lock.expiresAt.isAfter(now)) return false;

        lock.acquiredAt = now;
        lock.expiresAt = now.plus(ttl);
        work.run();
        lock.acquiredAt = Instant.now();
        lock.expiresAt = lock.acquiredAt.plus(ttl);
        return true;
    }

    private static void requirePositiveTtl(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl deve ser positivo");
        }
    }

    /**
     * Libera o lock, se ainda pertencer ao {@code ownerId}.
     * É idempotente: chamadas repetidas não geram erro.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void release(String jobName, String ownerId) {
        Objects.requireNonNull(jobName, "jobName");
        Objects.requireNonNull(ownerId, "ownerId");

        em.createNativeQuery(
                "DELETE FROM job_locks WHERE job_name = :name AND owner_id = :owner")
                .setParameter("name", jobName)
                .setParameter("owner", ownerId)
                .executeUpdate();

        LOG.debugf("Lock liberado: job=%s owner=%s", jobName, ownerId);
    }
}
