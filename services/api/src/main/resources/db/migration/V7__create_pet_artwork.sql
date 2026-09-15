-- =============================================================================
-- V7 — Arte e geração por IA (ART-01): metadados versionados, tentativas e
--       contexto congelado. Compatível com PostgreSQL e H2 (modo PostgreSQL).
-- =============================================================================

CREATE TABLE pet_artworks (
    id                       UUID                     NOT NULL,
    pet_id                   UUID                     NOT NULL,
    generation_status        VARCHAR(30)              NOT NULL,
    frozen_context           TEXT                     NOT NULL,
    prompt_private           TEXT                     NOT NULL,
    model_id                 VARCHAR(80)              NOT NULL,
    seed                     VARCHAR(80),
    asset_version            INTEGER                  NOT NULL DEFAULT 0,
    voluntary_regen_used     BOOLEAN                  NOT NULL DEFAULT FALSE,
    technical_attempt_count  INTEGER                  NOT NULL DEFAULT 0,
    last_failure_code        VARCHAR(60),
    last_failure_at          TIMESTAMP WITH TIME ZONE,
    next_retry_at            TIMESTAMP WITH TIME ZONE,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_pet_artworks              PRIMARY KEY (id),
    CONSTRAINT uq_pet_artworks_pet          UNIQUE (pet_id),
    CONSTRAINT fk_pet_artworks_pet          FOREIGN KEY (pet_id) REFERENCES pets (id) ON DELETE RESTRICT,
    CONSTRAINT chk_pet_artworks_status      CHECK (generation_status IN (
        'GENERATING', 'AWAITING_APPROVAL', 'APPROVED', 'RETRY_WAIT'
    )),
    CONSTRAINT chk_pet_artworks_asset_ver   CHECK (asset_version >= 0),
    CONSTRAINT chk_pet_artworks_tech_count  CHECK (technical_attempt_count >= 0)
);

CREATE INDEX idx_pet_artworks_status ON pet_artworks (generation_status);
CREATE INDEX idx_pet_artworks_retry ON pet_artworks (next_retry_at);

CREATE TABLE pet_artwork_attempts (
    id                  UUID                     NOT NULL,
    artwork_id          UUID                     NOT NULL,
    attempt_no          INTEGER                  NOT NULL,
    reason              VARCHAR(30)              NOT NULL,
    valid               BOOLEAN                  NOT NULL,
    rejection_reason    VARCHAR(120),
    storage_key_prefix  VARCHAR(255)             NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_pet_artwork_attempts        PRIMARY KEY (id),
    CONSTRAINT uq_pet_artwork_attempts_no       UNIQUE (artwork_id, attempt_no),
    CONSTRAINT fk_pet_artwork_attempts_artwork  FOREIGN KEY (artwork_id)
        REFERENCES pet_artworks (id) ON DELETE RESTRICT,
    CONSTRAINT chk_pet_artwork_attempts_reason  CHECK (reason IN (
        'INITIAL', 'VOLUNTARY_REGEN', 'TECHNICAL_RETRY'
    ))
);

CREATE INDEX idx_pet_artwork_attempts_artwork ON pet_artwork_attempts (artwork_id);
