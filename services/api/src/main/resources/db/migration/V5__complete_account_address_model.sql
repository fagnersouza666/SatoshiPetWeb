-- =============================================================================
-- V5 — Completa o modelo de conta, endereço e vínculo.
-- Compatível com PostgreSQL e H2 em modo PostgreSQL.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Endereços: a rede participa da identidade lógica do destino.
-- ---------------------------------------------------------------------------
ALTER TABLE addresses ADD COLUMN network VARCHAR(20);

UPDATE addresses
SET network = CASE
    WHEN LOWER(canonical) LIKE 'bcrt1%' THEN 'regtest'
    WHEN LOWER(canonical) LIKE 'bc1%'
         OR canonical LIKE '1%'
         OR canonical LIKE '3%' THEN 'mainnet'
    WHEN LOWER(canonical) LIKE 'tb1%'
         OR canonical LIKE 'm%'
         OR canonical LIKE 'n%'
         OR canonical LIKE '2%' THEN 'testnet'
    ELSE NULL
END;

ALTER TABLE addresses ALTER COLUMN network SET NOT NULL;
ALTER TABLE addresses DROP CONSTRAINT uq_addresses_canonical;
ALTER TABLE addresses ADD CONSTRAINT uq_addresses_network_canonical
    UNIQUE (network, canonical);
ALTER TABLE addresses ADD CONSTRAINT chk_addresses_network
    CHECK (network IN ('mainnet', 'testnet', 'regtest'));

-- ---------------------------------------------------------------------------
-- Vínculos: um único vínculo ativo por conta e intervalo temporal válido.
-- ---------------------------------------------------------------------------
ALTER TABLE account_address_bindings ADD CONSTRAINT chk_account_address_bindings_interval
    CHECK (unbound_at IS NULL OR unbound_at >= bound_at);

-- O marcador primário representa o vínculo ativo: históricos ficam nulos,
-- permitindo vários registros encerrados na constraint UNIQUE.
ALTER TABLE account_address_bindings ALTER COLUMN is_primary DROP NOT NULL;
UPDATE account_address_bindings
SET is_primary = CASE WHEN unbound_at IS NULL THEN TRUE ELSE NULL END;
ALTER TABLE account_address_bindings ADD CONSTRAINT chk_account_address_bindings_state
    CHECK ((unbound_at IS NULL AND is_primary = TRUE)
           OR (unbound_at IS NOT NULL AND is_primary IS NULL));
ALTER TABLE account_address_bindings ADD CONSTRAINT uq_account_address_bindings_active_account
    UNIQUE (account_id, is_primary);
