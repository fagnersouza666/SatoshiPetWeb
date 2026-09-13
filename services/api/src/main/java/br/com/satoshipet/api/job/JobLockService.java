package br.com.satoshipet.api.job;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
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
