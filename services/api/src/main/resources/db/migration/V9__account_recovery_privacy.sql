-- Sessões anteriores usavam CSRF aleatório irrecuperável; invalidação única na migração.
UPDATE sessions SET invalidated_at = CURRENT_TIMESTAMP WHERE invalidated_at IS NULL;

-- Trava curta para mutações estruturais de contas; a transação é a duração da trava.
CREATE TABLE account_mutation_locks (lock_name VARCHAR(40) PRIMARY KEY);
INSERT INTO account_mutation_locks(lock_name) VALUES ('accounts');

-- A criatura permanece; a associação pessoal de seu criador/fonte é removível.
ALTER TABLE pets ALTER COLUMN creator_account_id DROP NOT NULL;
ALTER TABLE pets DROP CONSTRAINT fk_pets_creator_account;
ALTER TABLE pets ADD CONSTRAINT fk_pets_creator_account
    FOREIGN KEY (creator_account_id) REFERENCES accounts(id) ON DELETE SET NULL;
ALTER TABLE pets DROP CONSTRAINT fk_pets_food_source_account;
ALTER TABLE pets ADD CONSTRAINT fk_pets_food_source_account
    FOREIGN KEY (food_source_account_id) REFERENCES accounts(id) ON DELETE SET NULL;

CREATE TABLE recovery_email_tokens (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    recovery_code_id UUID NOT NULL REFERENCES recovery_codes(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    new_email VARCHAR(320) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_recovery_email_expiry CHECK (expires_at > issued_at)
);
CREATE INDEX idx_recovery_email_account ON recovery_email_tokens(account_id);

-- Não contém e-mail, endereço, códigos ou outros dados pessoais da conta.
-- Preservar/exportar este diário separadamente antes de restaurar backups antigos.
CREATE TABLE account_deletion_tombstones (
    account_id UUID PRIMARY KEY,
    deleted_at TIMESTAMP WITH TIME ZONE NOT NULL
);
