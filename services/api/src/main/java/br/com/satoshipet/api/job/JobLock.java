package br.com.satoshipet.api.job;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Trava distribuída por nome de job, com TTL para evitar deadlock. */
@Entity
@Table(name = "job_locks")
public class JobLock extends PanacheEntityBase {

    /** Nome único do job (chave primária). */
    @Id
    @Column(name = "job_name", nullable = false, updatable = false, length = 100)
    public String jobName;

    /** Identificador da instância/thread que detém a trava. */
    @Column(name = "owner_id", nullable = false, length = 100)
    public String ownerId;

    /** Momento em que a trava foi adquirida ou renovada. */
    @Column(name = "acquired_at", nullable = false)
    public Instant acquiredAt;

    /** Expiração da trava; após este instante qualquer instância pode assumir. */
    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    protected JobLock() {
        // Construtor exigido pelo Hibernate ORM.
    }

    JobLock(String jobName, String ownerId, Instant acquiredAt, Instant expiresAt) {
        this.jobName = jobName;
        this.ownerId = ownerId;
        this.acquiredAt = acquiredAt;
        this.expiresAt = expiresAt;
    }
}
