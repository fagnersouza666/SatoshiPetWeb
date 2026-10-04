package br.com.satoshipet.api.realtime;

import br.com.satoshipet.api.outbox.OutboxEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/** Recibo do consumidor e cursor estável; o payload permanece somente na outbox. */
@Entity
@Table(name = "realtime_event_receipts")
public class RealtimeEventReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "realtime-cursor")
    @SequenceGenerator(name = "realtime-cursor", sequenceName = "realtime_event_cursor_seq", allocationSize = 1)
    public Long cursor;

    @OneToOne(optional = false)
    @JoinColumn(name = "event_id", nullable = false, unique = true, updatable = false)
    public OutboxEvent event;

    @Column(name = "canonical", nullable = false, length = 100, updatable = false)
    public String canonical;
}
