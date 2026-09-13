package br.com.satoshipet.api.job;

import jakarta.enterprise.context.ApplicationScoped;
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
 * Usa uma estratégia de DELETE do lock expirado + INSERT atômico dentro de
 * {@code REQUIRES_NEW}, de modo que falhas de concorrência nunca poluem a
 * transação chamadora.</p>
 */
@ApplicationScoped
public class JobLockService {

    private static final Logger LOG = Logger.getLogger(JobLockService.class);

    private final EntityManager em;

    public JobLockService(EntityManager em) {
        this.em = em;
    }

    /**
     * Tenta adquirir a trava para o job informado.
     *
     * <p>A operação roda numa transação independente ({@code REQUIRES_NEW})
     * para isolar eventuais falhas de unicidade sem afetar a transação
     * chamadora.</p>
     *
     * @param jobName nome único do job
     * @param ownerId identificador da instância/thread que requisita a trava
     * @param ttl     tempo de vida da trava; expirado, qualquer instância pode assumir
     * @return {@code true} se a trava foi adquirida ou já pertencia ao {@code ownerId}
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean acquire(String jobName, String ownerId, Duration ttl) {
        Objects.requireNonNull(jobName, "jobName");
        Objects.requireNonNull(ownerId, "ownerId");
        requirePositiveTtl(ttl);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        // Remove lock expirado de qualquer proprietário para liberar o slot.
        int deleted = em.createNativeQuery(
                "DELETE FROM job_locks WHERE job_name = :name AND expires_at < :now")
                .setParameter("name", jobName)
                .setParameter("now", now)
                .executeUpdate();

        if (deleted > 0) {
            LOG.debugf("Lock expirado removido para job=%s", jobName);
        }

        try {
            // Tenta inserir o novo lock.
            em.createNativeQuery(
                    "INSERT INTO job_locks (job_name, owner_id, acquired_at, expires_at) "
                    + "VALUES (:name, :owner, :acquired, :exp)")
                    .setParameter("name", jobName)
                    .setParameter("owner", ownerId)
                    .setParameter("acquired", now)
                    .setParameter("exp", expiresAt)
                    .executeUpdate();

            LOG.debugf("Lock adquirido: job=%s owner=%s", jobName, ownerId);
            return true;

        } catch (PersistenceException e) {
            // Violação de unicidade — outra instância inseriu antes.
            LOG.debugf("Lock já detido por outro owner: job=%s", jobName);
            return false;
        }
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
