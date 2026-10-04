-- Identidade lógica persistente através de substituições por inputs conflitantes.
ALTER TABLE bitcoin_transactions ADD COLUMN logical_receipt_id UUID;
UPDATE bitcoin_transactions SET logical_receipt_id = (
    SELECT r.id FROM logical_receipts r
    WHERE r.address_id = bitcoin_transactions.address_id AND r.reference_txid = bitcoin_transactions.txid
);
ALTER TABLE bitcoin_transactions ADD CONSTRAINT fk_bitcoin_transactions_receipt
    FOREIGN KEY (logical_receipt_id) REFERENCES logical_receipts(id) ON DELETE SET NULL;
CREATE INDEX idx_bitcoin_transactions_receipt ON bitcoin_transactions(logical_receipt_id);

CREATE TABLE bitcoin_inputs (
    id UUID NOT NULL PRIMARY KEY,
    transaction_id UUID NOT NULL,
    previous_txid VARCHAR(64) NOT NULL,
    previous_vout INTEGER NOT NULL,
    CONSTRAINT fk_bitcoin_inputs_transaction FOREIGN KEY (transaction_id)
        REFERENCES bitcoin_transactions(id) ON DELETE CASCADE,
    CONSTRAINT uq_bitcoin_inputs_outpoint UNIQUE (transaction_id, previous_txid, previous_vout),
    CONSTRAINT chk_bitcoin_inputs_vout CHECK (previous_vout >= 0)
);
CREATE INDEX idx_bitcoin_inputs_conflict ON bitcoin_inputs(previous_txid, previous_vout);
