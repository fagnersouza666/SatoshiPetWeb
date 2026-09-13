package br.com.satoshipet.api.job;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;

/**
 * Executa a tentativa de aquisição dentro de uma transação própria.
 *
 * <p>A separação permite que uma colisão de chave no adaptador H2 de testes
 * seja revertida antes de o serviço traduzir a contenção para {@code false}.
 * Em PostgreSQL, a operação usa o upsert nativo e não depende de exceção para
 * sinalizar uma trava ativa.</p>
 */
@ApplicationScoped
class JobLockAcquisition {

    private final EntityManager em;
    private final boolean postgresql;

    JobLockAcquisition(
            EntityManager em,
            @ConfigProperty(name = "quarkus.datasource.db-kind", defaultValue = "postgresql") String dbKind
    ) {
        this.em = em;
        this.postgresql = "postgresql".equalsIgnoreCase(dbKind);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    int execute(String jobName, String ownerId, Instant acquiredAt, Instant expiresAt) {
        return postgresql
                ? executePostgresql(jobName, ownerId, acquiredAt, expiresAt)
                : executeWithMerge(jobName, ownerId, acquiredAt, expiresAt);
    }

    private int executePostgresql(String jobName, String ownerId, Instant acquiredAt, Instant expiresAt) {
        return em.createNativeQuery(
                        "INSERT INTO job_locks (job_name, owner_id, acquired_at, expires_at) "
                        + "VALUES (:name, :owner, :acquired, :exp) "
                        + "ON CONFLICT (job_name) DO UPDATE SET "
                        + "owner_id = EXCLUDED.owner_id, "
                        + "acquired_at = EXCLUDED.acquired_at, "
                        + "expires_at = EXCLUDED.expires_at "
                        + "WHERE job_locks.expires_at < EXCLUDED.acquired_at")
                .setParameter("name", jobName)
                .setParameter("owner", ownerId)
                .setParameter("acquired", acquiredAt)
                .setParameter("exp", expiresAt)
                .executeUpdate();
    }

    private int executeWithMerge(String jobName, String ownerId, Instant acquiredAt, Instant expiresAt) {
        return em.createNativeQuery(
                        "MERGE INTO job_locks AS target "
                        + "USING (VALUES (:name, :owner, :acquired, :exp)) "
                        + "AS source(job_name, owner_id, acquired_at, expires_at) "
                        + "ON target.job_name = source.job_name "
                        + "WHEN MATCHED AND target.expires_at < source.acquired_at THEN "
                        + "UPDATE SET owner_id = source.owner_id, "
                        + "acquired_at = source.acquired_at, expires_at = source.expires_at "
                        + "WHEN NOT MATCHED THEN INSERT "
                        + "(job_name, owner_id, acquired_at, expires_at) "
                        + "VALUES (source.job_name, source.owner_id, source.acquired_at, source.expires_at)")
                .setParameter("name", jobName)
                .setParameter("owner", ownerId)
                .setParameter("acquired", acquiredAt)
                .setParameter("exp", expiresAt)
                .executeUpdate();
    }
}
