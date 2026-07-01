-- =============================================================================
-- V202604261205__parametro_macro_tpm_q2q3.sql
-- Add tpm_anual_q2 and tpm_anual_q3 for per-quarter TPM (was only Q1/Q4)
-- Enables cyclic Q5+ macro params without interpolation artifacts
-- =============================================================================

SET search_path TO sim, public;

ALTER TABLE sim.parametro_macro
    ADD COLUMN tpm_anual_q2 NUMERIC(6,4),
    ADD COLUMN tpm_anual_q3 NUMERIC(6,4);

-- Backfill: interpolate Q2 and Q3 from existing Q1/Q4
UPDATE sim.parametro_macro
SET tpm_anual_q2 = tpm_anual_q1 + (tpm_anual_q4 - tpm_anual_q1) / 3,
    tpm_anual_q3 = tpm_anual_q1 + (tpm_anual_q4 - tpm_anual_q1) * 2 / 3;

ALTER TABLE sim.parametro_macro
    ALTER COLUMN tpm_anual_q2 SET NOT NULL,
    ALTER COLUMN tpm_anual_q3 SET NOT NULL;

ALTER TABLE sim.parametro_macro
    ADD CONSTRAINT chk_pm_tpm_q2 CHECK (tpm_anual_q2 >= 0),
    ADD CONSTRAINT chk_pm_tpm_q3 CHECK (tpm_anual_q3 >= 0);

-- DOWN
-- ALTER TABLE sim.parametro_macro DROP COLUMN tpm_anual_q2, DROP COLUMN tpm_anual_q3;
