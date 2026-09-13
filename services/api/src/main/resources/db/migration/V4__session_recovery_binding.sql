-- =============================================================================
-- V4 — Recuperação de sessão e vínculos: token de sessão com hash, CSRF,
--       códigos de recuperação, desvinculação de endereço.
-- Compatível com PostgreSQL (produção) e H2 no modo PostgreSQL (testes).
-- Deve preceder qualquer dado de sessão real; tabela sessions vazia ao migrar.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- sessions: adiciona token_hash (verificador seguro) e csrf_token_hash
-- ---------------------------------------------------------------------------
ALTER TABLE sessions ADD COLUMN token_hash VARCHAR(64) DEFAULT '' NOT NULL;
ALTER TABLE sessions ALTER COLUMN token_hash DROP DEFAULT;
ALTER TABLE sessions ADD COLUMN csrf_token_hash VARCHAR(64);

CREATE UNIQUE INDEX uq_sessions_token_hash ON sessions (token_hash);

-- ---------------------------------------------------------------------------
-- account_address_bindings: marca quando um vínculo foi desfeito (nullable)
-- ---------------------------------------------------------------------------
ALTER TABLE account_address_bindings ADD COLUMN unbound_at TIMESTAMP WITH TIME ZONE;

-- ---------------------------------------------------------------------------
-- Códigos de recuperação — gerados uma vez, armazenados apenas como hash
-- Cada conta pode ter no máximo um conjunto ativo (gerenciado pelo serviço).
-- ---------------------------------------------------------------------------
CREATE TABLE recovery_codes (
    id         UUID                     NOT NULL,
    account_id UUID                     NOT NULL,
    code_hash  VARCHAR(64)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_recovery_codes           PRIMARY KEY (id),
    CONSTRAINT uq_recovery_codes_code_hash UNIQUE      (code_hash),
    CONSTRAINT fk_recovery_codes_account   FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    CONSTRAINT chk_recovery_codes_used     CHECK (used_at IS NULL OR used_at >= created_at)
);

CREATE INDEX idx_recovery_codes_account ON recovery_codes (account_id);
