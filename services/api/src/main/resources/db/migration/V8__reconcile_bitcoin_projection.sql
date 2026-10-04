-- Identidade por endereço e saldo reconciliado (não confundir recebimentos com saldo).
ALTER TABLE bitcoin_transactions DROP CONSTRAINT uq_bitcoin_transactions_txid;
ALTER TABLE bitcoin_transactions ADD CONSTRAINT uq_bitcoin_transactions_address_txid UNIQUE (address_id, txid);
CREATE INDEX idx_bitcoin_transactions_txid ON bitcoin_transactions (txid);
ALTER TABLE address_monitor_state ADD COLUMN confirmed_balance_sats BIGINT;
ALTER TABLE address_monitor_state ADD COLUMN pending_balance_sats BIGINT;
ALTER TABLE address_monitor_state ADD COLUMN balance_checked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE address_monitor_state ADD COLUMN provider_available BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE address_monitor_state ADD COLUMN backfill_complete BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE address_monitor_state ADD COLUMN last_published_confirmed_sats BIGINT;
ALTER TABLE address_monitor_state ADD COLUMN last_published_pending_sats BIGINT;
