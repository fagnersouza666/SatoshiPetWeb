ALTER TABLE pet_feedings ADD COLUMN initial_amount_sats BIGINT;
ALTER TABLE pet_feedings ADD COLUMN initial_portion_sats BIGINT;
ALTER TABLE pet_feedings ADD COLUMN initial_duration_hours NUMERIC(20, 10);
ALTER TABLE pet_feedings ADD COLUMN credit_effective_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pet_feedings ADD COLUMN reserve_eligible BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE pet_feedings SET initial_amount_sats = amount_sats,
    initial_portion_sats = portion_sats, initial_duration_hours = duration_hours,
    credit_effective_at = effective_at,
    reserve_eligible = (status = 'VALID' OR presentable = TRUE);
ALTER TABLE pet_feedings ALTER COLUMN initial_amount_sats SET NOT NULL;
ALTER TABLE pet_feedings ALTER COLUMN initial_portion_sats SET NOT NULL;
ALTER TABLE pet_feedings ALTER COLUMN initial_duration_hours SET NOT NULL;
ALTER TABLE pet_feedings ALTER COLUMN credit_effective_at SET NOT NULL;

ALTER TABLE pets ADD COLUMN birth_foundation_lost BOOLEAN NOT NULL DEFAULT FALSE;
