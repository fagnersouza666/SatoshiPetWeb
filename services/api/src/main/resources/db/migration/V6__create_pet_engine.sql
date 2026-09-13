-- =============================================================================
-- V6 — Motor do pet: reserva, estado emocional, alimentações, porção de
--       referência e cursor de apresentação (PET-01, PET-06).
-- Compatível com PostgreSQL (produção) e H2 no modo PostgreSQL (testes).
-- Não recria pets; amplia colunas e devolve valores iniciais aos registros
-- já existentes (fonte alimentar = criador, reserva 0, apresentação OVO).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- pets: colunas do motor de alimentação e apresentação
-- ---------------------------------------------------------------------------
ALTER TABLE pets ADD COLUMN food_source_account_id UUID;
ALTER TABLE pets ADD COLUMN last_positive_portion_sats BIGINT;
ALTER TABLE pets ADD COLUMN last_positive_portion_origin VARCHAR(40);
ALTER TABLE pets ADD COLUMN reserve_hours NUMERIC(20, 10) DEFAULT 0 NOT NULL;
ALTER TABLE pets ADD COLUMN reserve_depleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pets ADD COLUMN last_evaluated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pets ADD COLUMN emotional_state VARCHAR(20) DEFAULT 'ALIMENTADO' NOT NULL;
ALTER TABLE pets ADD COLUMN presentation VARCHAR(20) DEFAULT 'EGG' NOT NULL;
ALTER TABLE pets ADD COLUMN born_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pets ADD COLUMN zero_balance_since TIMESTAMP WITH TIME ZONE;
ALTER TABLE pets ADD COLUMN awaiting_reference BOOLEAN DEFAULT true NOT NULL;
ALTER TABLE pets ADD COLUMN artwork_status VARCHAR(20) DEFAULT 'NONE' NOT NULL;
ALTER TABLE pets ADD COLUMN last_returned_to_egg_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pets ADD COLUMN last_reappeared_at TIMESTAMP WITH TIME ZONE;

UPDATE pets
   SET food_source_account_id = creator_account_id,
       last_evaluated_at      = created_at
 WHERE food_source_account_id IS NULL
    OR last_evaluated_at IS NULL;

ALTER TABLE pets ALTER COLUMN last_evaluated_at SET NOT NULL;
ALTER TABLE pets ALTER COLUMN reserve_hours DROP DEFAULT;
ALTER TABLE pets ALTER COLUMN emotional_state DROP DEFAULT;
ALTER TABLE pets ALTER COLUMN presentation DROP DEFAULT;
ALTER TABLE pets ALTER COLUMN awaiting_reference DROP DEFAULT;
ALTER TABLE pets ALTER COLUMN artwork_status DROP DEFAULT;

ALTER TABLE pets ADD CONSTRAINT fk_pets_food_source_account
    FOREIGN KEY (food_source_account_id) REFERENCES accounts (id) ON DELETE RESTRICT;
ALTER TABLE pets ADD CONSTRAINT chk_pets_last_positive_portion_sats
    CHECK (last_positive_portion_sats IS NULL OR last_positive_portion_sats >= 1);
ALTER TABLE pets ADD CONSTRAINT chk_pets_last_positive_portion_origin
    CHECK (last_positive_portion_origin IS NULL OR last_positive_portion_origin IN (
        'CREATOR_PLAN', 'FALLBACK_OLDEST_BINDING', 'TECHNICAL_RECONSTRUCTION'
    ));
ALTER TABLE pets ADD CONSTRAINT chk_pets_reserve_hours
    CHECK (reserve_hours >= 0 AND reserve_hours <= 168);
ALTER TABLE pets ADD CONSTRAINT chk_pets_emotional_state
    CHECK (emotional_state IN (
        'ALIMENTADO', 'PENSANDO', 'CHATEADO', 'FAMINTO', 'CRITICO', 'HIBERNANDO'
    ));
ALTER TABLE pets ADD CONSTRAINT chk_pets_presentation
    CHECK (presentation IN ('EGG', 'CREATURE'));
ALTER TABLE pets ADD CONSTRAINT chk_pets_artwork_status
    CHECK (artwork_status IN ('NONE', 'PENDING', 'APPROVED'));

-- ---------------------------------------------------------------------------
-- pet_feedings: uma alimentação por recebimento lógico (CA-017)
-- ---------------------------------------------------------------------------
CREATE TABLE pet_feedings (
    id                 UUID                     NOT NULL,
    pet_id             UUID                     NOT NULL,
    logical_receipt_id UUID                     NOT NULL,
    amount_sats        BIGINT                   NOT NULL,
    portion_sats       BIGINT                   NOT NULL,
    duration_hours     NUMERIC(20, 10)          NOT NULL,
    effective_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    rule_version       VARCHAR(40)              NOT NULL,
    status             VARCHAR(20)              NOT NULL,
    origin             VARCHAR(40)              NOT NULL,
    presentable        BOOLEAN                  NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_pet_feedings                  PRIMARY KEY (id),
    CONSTRAINT uq_pet_feedings_pet_receipt      UNIQUE (pet_id, logical_receipt_id),
    CONSTRAINT fk_pet_feedings_pet              FOREIGN KEY (pet_id) REFERENCES pets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pet_feedings_logical_receipt  FOREIGN KEY (logical_receipt_id) REFERENCES logical_receipts (id) ON DELETE RESTRICT,
    CONSTRAINT chk_pet_feedings_amount          CHECK (amount_sats >= 0),
    CONSTRAINT chk_pet_feedings_portion         CHECK (portion_sats > 0),
    CONSTRAINT chk_pet_feedings_duration        CHECK (duration_hours >= 0),
    CONSTRAINT chk_pet_feedings_status          CHECK (status IN ('PROVISIONAL', 'VALID', 'INVALIDATED')),
    CONSTRAINT chk_pet_feedings_origin          CHECK (origin IN ('LIVE', 'HISTORICAL_RECONSTRUCTION'))
);

CREATE INDEX idx_pet_feedings_pet ON pet_feedings (pet_id);

-- ---------------------------------------------------------------------------
-- pet_reference_portions: histórico da porção de 24h (CC-05)
-- ---------------------------------------------------------------------------
CREATE TABLE pet_reference_portions (
    id                UUID                     NOT NULL,
    pet_id            UUID                     NOT NULL,
    source_account_id UUID,
    portion_sats      BIGINT                   NOT NULL,
    valid_from        TIMESTAMP WITH TIME ZONE NOT NULL,
    origin            VARCHAR(40)              NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_pet_reference_portions                PRIMARY KEY (id),
    CONSTRAINT fk_pet_reference_portions_pet            FOREIGN KEY (pet_id) REFERENCES pets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pet_reference_portions_source_account FOREIGN KEY (source_account_id) REFERENCES accounts (id) ON DELETE SET NULL,
    CONSTRAINT chk_pet_reference_portions_sats          CHECK (portion_sats > 0),
    CONSTRAINT chk_pet_reference_portions_origin        CHECK (origin IN (
        'CREATOR_PLAN', 'FALLBACK_OLDEST_BINDING', 'TECHNICAL_RECONSTRUCTION'
    ))
);

CREATE INDEX idx_pet_reference_portions_pet_valid_from
    ON pet_reference_portions (pet_id, valid_from);

-- ---------------------------------------------------------------------------
-- presentation_cursors: último evento apresentado por conta
-- ---------------------------------------------------------------------------
CREATE TABLE presentation_cursors (
    id                       UUID                     NOT NULL,
    account_id               UUID                     NOT NULL,
    last_presented_event_id  UUID,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_presentation_cursors         PRIMARY KEY (id),
    CONSTRAINT uq_presentation_cursors_account UNIQUE (account_id),
    CONSTRAINT fk_presentation_cursors_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE
);
