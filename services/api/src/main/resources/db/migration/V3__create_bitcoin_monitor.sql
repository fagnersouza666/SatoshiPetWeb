-- =============================================================================
-- V3 — Monitor Bitcoin: transações, saídas, gastos, recebimentos lógicos,
--       estado do monitor por endereço.
-- Compatível com PostgreSQL (produção) e H2 no modo PostgreSQL (testes).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Transações Bitcoin observadas pelo indexador
-- ---------------------------------------------------------------------------
CREATE TABLE bitcoin_transactions (
    id           UUID                     NOT NULL,
    txid         VARCHAR(64)              NOT NULL,
    address_id   UUID                     NOT NULL,
    status       VARCHAR(20)              NOT NULL,
    amount_sats  BIGINT                   NOT NULL,
    observed_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE,
    block_height INTEGER,
    block_hash   VARCHAR(64),

    CONSTRAINT pk_bitcoin_transactions         PRIMARY KEY (id),
    CONSTRAINT uq_bitcoin_transactions_txid    UNIQUE      (txid),
    CONSTRAINT fk_bitcoin_transactions_address FOREIGN KEY (address_id) REFERENCES addresses (id) ON DELETE RESTRICT,
    CONSTRAINT chk_bitcoin_transactions_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REPLACED', 'DROPPED')),
    CONSTRAINT chk_bitcoin_transactions_sats   CHECK (amount_sats >= 0)
);

CREATE INDEX idx_bitcoin_transactions_address ON bitcoin_transactions (address_id);
CREATE INDEX idx_bitcoin_transactions_status  ON bitcoin_transactions (status);

-- ---------------------------------------------------------------------------
-- Saídas (outputs) individuais de cada transação
-- ---------------------------------------------------------------------------
CREATE TABLE bitcoin_outputs (
    id           UUID        NOT NULL,
    transaction_id UUID      NOT NULL,
    output_index INTEGER     NOT NULL,
    address_id   UUID        NOT NULL,
    value_sats   BIGINT      NOT NULL,
    script       TEXT,

    CONSTRAINT pk_bitcoin_outputs              PRIMARY KEY (id),
    CONSTRAINT uq_bitcoin_outputs_vout         UNIQUE (transaction_id, output_index),
    CONSTRAINT fk_bitcoin_outputs_transaction  FOREIGN KEY (transaction_id) REFERENCES bitcoin_transactions (id) ON DELETE CASCADE,
    CONSTRAINT fk_bitcoin_outputs_address      FOREIGN KEY (address_id)     REFERENCES addresses            (id) ON DELETE RESTRICT,
    CONSTRAINT chk_bitcoin_outputs_value       CHECK (value_sats >= 0)
);

CREATE INDEX idx_bitcoin_outputs_transaction ON bitcoin_outputs (transaction_id);
CREATE INDEX idx_bitcoin_outputs_address     ON bitcoin_outputs (address_id);

-- ---------------------------------------------------------------------------
-- Gastos (spends): output gasto por txid subsequente
-- ---------------------------------------------------------------------------
CREATE TABLE bitcoin_spends (
    id            UUID                     NOT NULL,
    output_id     UUID                     NOT NULL,
    spending_txid VARCHAR(64)              NOT NULL,
    observed_at   TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_bitcoin_spends         PRIMARY KEY (id),
    CONSTRAINT uq_bitcoin_spends_output  UNIQUE      (output_id),
    CONSTRAINT fk_bitcoin_spends_output  FOREIGN KEY (output_id) REFERENCES bitcoin_outputs (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Recebimentos lógicos: agregado imutável por (address, txid_referência)
-- Snapshots de confirmados + pendentes; reorg/RBF recalculam sem duplicar.
-- ---------------------------------------------------------------------------
CREATE TABLE logical_receipts (
    id              UUID                     NOT NULL,
    address_id      UUID                     NOT NULL,
    reference_txid  VARCHAR(64)              NOT NULL,
    amount_sats     BIGINT                   NOT NULL,
    confirmed_sats  BIGINT                   NOT NULL DEFAULT 0,
    pending_sats    BIGINT                   NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_logical_receipts             PRIMARY KEY (id),
    CONSTRAINT uq_logical_receipts_addr_txid   UNIQUE (address_id, reference_txid),
    CONSTRAINT fk_logical_receipts_address     FOREIGN KEY (address_id) REFERENCES addresses (id) ON DELETE RESTRICT,
    CONSTRAINT chk_logical_receipts_sats       CHECK (amount_sats >= 0 AND confirmed_sats >= 0 AND pending_sats >= 0),
    CONSTRAINT chk_logical_receipts_updated    CHECK (updated_at >= created_at)
);

CREATE INDEX idx_logical_receipts_address ON logical_receipts (address_id);

-- ---------------------------------------------------------------------------
-- Estado do monitor por endereço (cursor de eventos do indexador)
-- ---------------------------------------------------------------------------
CREATE TABLE address_monitor_state (
    address_id       UUID                     NOT NULL,
    last_seen_txid   VARCHAR(64),
    last_checked_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    cursor           TEXT,

    CONSTRAINT pk_address_monitor_state        PRIMARY KEY (address_id),
    CONSTRAINT fk_address_monitor_state_address FOREIGN KEY (address_id) REFERENCES addresses (id) ON DELETE CASCADE
);
