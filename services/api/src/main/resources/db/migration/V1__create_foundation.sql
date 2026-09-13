-- =============================================================================
-- V1 — Fundação: contas, endereços, sessões, outbox, job_locks, pets
-- Compatível com PostgreSQL (produção) e H2 no modo PostgreSQL (testes).
-- DEVE preceder V2 (magic_link_tokens) na ordem de migração.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Contas de usuário
-- ---------------------------------------------------------------------------
CREATE TABLE accounts (
    id                     UUID                     NOT NULL,
    email                  VARCHAR(320)             NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    timezone               VARCHAR(50)              NOT NULL DEFAULT 'America/Sao_Paulo',
    locale                 VARCHAR(10)              NOT NULL DEFAULT 'pt-BR',
    address_change_deadline TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_accounts           PRIMARY KEY (id),
    CONSTRAINT uq_accounts_email     UNIQUE      (email),
    CONSTRAINT chk_accounts_email_len CHECK (LENGTH(email) BETWEEN 3 AND 320)
);

CREATE INDEX idx_accounts_email ON accounts (email);

-- ---------------------------------------------------------------------------
-- Endereços Bitcoin canônicos
-- ---------------------------------------------------------------------------
CREATE TABLE addresses (
    id           UUID                     NOT NULL,
    canonical    VARCHAR(90)              NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_addresses           PRIMARY KEY (id),
    CONSTRAINT uq_addresses_canonical UNIQUE      (canonical)
);

-- ---------------------------------------------------------------------------
-- Vínculo entre conta e endereço (N contas : N endereços)
-- ---------------------------------------------------------------------------
CREATE TABLE account_address_bindings (
    id         UUID                     NOT NULL,
    account_id UUID                     NOT NULL,
    address_id UUID                     NOT NULL,
    bound_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    is_primary BOOLEAN                  NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_account_address_bindings          PRIMARY KEY (id),
    CONSTRAINT uq_account_address_bindings_pair     UNIQUE (account_id, address_id),
    CONSTRAINT fk_account_address_bindings_account  FOREIGN KEY (account_id) REFERENCES accounts (id)  ON DELETE CASCADE,
    CONSTRAINT fk_account_address_bindings_address  FOREIGN KEY (address_id) REFERENCES addresses (id) ON DELETE RESTRICT
);

CREATE INDEX idx_account_address_bindings_account ON account_address_bindings (account_id);
CREATE INDEX idx_account_address_bindings_address ON account_address_bindings (address_id);

-- ---------------------------------------------------------------------------
-- Sessões autenticadas
-- ---------------------------------------------------------------------------
CREATE TABLE sessions (
    id             UUID                     NOT NULL,
    account_id     UUID                     NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    invalidated_at TIMESTAMP WITH TIME ZONE,
    user_agent     TEXT,
    ip_address     VARCHAR(45),

    CONSTRAINT pk_sessions             PRIMARY KEY (id),
    CONSTRAINT fk_sessions_account     FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    CONSTRAINT chk_sessions_expiration CHECK (expires_at > created_at)
);

CREATE INDEX idx_sessions_account    ON sessions (account_id);
CREATE INDEX idx_sessions_expires_at ON sessions (expires_at);

-- ---------------------------------------------------------------------------
-- Outbox transacional — eventos de domínio a publicar
-- ---------------------------------------------------------------------------
CREATE TABLE outbox_events (
    id             UUID                     NOT NULL,
    aggregate_type VARCHAR(100)             NOT NULL,
    aggregate_id   VARCHAR(100)             NOT NULL,
    event_type     VARCHAR(100)             NOT NULL,
    payload        TEXT                     NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at   TIMESTAMP WITH TIME ZONE,
    correlation_id VARCHAR(36),
    retries        INTEGER                  NOT NULL DEFAULT 0,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

-- Índice para o poll do OutboxPublisher (pendentes ordenados pelo mais antigo)
CREATE INDEX idx_outbox_events_pending ON outbox_events (processed_at, created_at);

-- ---------------------------------------------------------------------------
-- Travas distribuídas por nome de job
-- ---------------------------------------------------------------------------
CREATE TABLE job_locks (
    job_name    VARCHAR(100)             NOT NULL,
    owner_id    VARCHAR(100)             NOT NULL,
    acquired_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_job_locks PRIMARY KEY (job_name)
);

-- ---------------------------------------------------------------------------
-- Pets (criatura persistente de cada endereço — mínimo para FUND)
-- ---------------------------------------------------------------------------
CREATE TABLE pets (
    id                  UUID                     NOT NULL,
    address_id          UUID                     NOT NULL,
    creator_account_id  UUID                     NOT NULL,
    name                VARCHAR(100)             NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_pets                    PRIMARY KEY (id),
    CONSTRAINT uq_pets_address            UNIQUE      (address_id),
    CONSTRAINT fk_pets_address            FOREIGN KEY (address_id)         REFERENCES addresses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pets_creator_account    FOREIGN KEY (creator_account_id) REFERENCES accounts  (id) ON DELETE RESTRICT,
    CONSTRAINT chk_pets_updated_after_creation CHECK (updated_at >= created_at)
);
