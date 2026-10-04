package br.com.satoshipet.api.isolation;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Identificador opaco para reaplicar exclusão após restauração, sem e-mail ou endereço. */
@Entity
@Table(name = "account_deletion_tombstones")
public class AccountDeletionTombstone extends PanacheEntityBase {
    @Id @Column(name = "account_id") public UUID accountId;
    @Column(name = "deleted_at", nullable = false) public Instant deletedAt;
}
