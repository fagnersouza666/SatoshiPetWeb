-- Projeção/recibo durável do consumidor WebSocket. A retenção acompanha a outbox.
CREATE SEQUENCE realtime_event_cursor_seq START WITH 1 INCREMENT BY 1;

-- A trava desta linha dura até o commit: ordem da sequência = ordem de visibilidade.
CREATE TABLE realtime_cursor_clock (
    id INTEGER PRIMARY KEY,
    CONSTRAINT chk_realtime_cursor_clock_singleton CHECK (id = 1)
);
INSERT INTO realtime_cursor_clock (id) VALUES (1);

CREATE TABLE realtime_event_receipts (
    cursor BIGINT NOT NULL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    canonical VARCHAR(100) NOT NULL,
    CONSTRAINT fk_realtime_receipt_outbox FOREIGN KEY (event_id)
        REFERENCES outbox_events (id) ON DELETE CASCADE
);
CREATE INDEX idx_realtime_receipts_channel_cursor ON realtime_event_receipts (canonical, cursor);
