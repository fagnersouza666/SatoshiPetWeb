package br.com.satoshipet.api.pet;

import br.com.satoshipet.api.account.Account;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Cursor do último evento de apresentação já exibido para uma conta. */
@Entity
@Table(
        name = "presentation_cursors",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_presentation_cursors_account", columnNames = "account_id")
        }
)
public class PresentationCursor extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false, unique = true)
    public Account account;

    /** Id do evento de outbox já apresentado; sem FK até o catálogo de eventos. */
    @Column(name = "last_presented_event_id")
    public UUID lastPresentedEventId;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    protected PresentationCursor() {
        // Construtor exigido pelo Hibernate ORM.
    }

    public static PresentationCursor create(Account account, Instant now) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(now, "now");

        PresentationCursor cursor = new PresentationCursor();
        cursor.id = UUID.randomUUID();
        cursor.account = account;
        cursor.lastPresentedEventId = null;
        cursor.updatedAt = now;
        return cursor;
    }

    public static Optional<PresentationCursor> findByAccount(Account account) {
        return find("account", account).firstResultOptional();
    }
}
