-- =============================================================================
-- V202604261201__escenario_rubro_id.sql
-- Add rubro_id FK to escenario_predefinido for sector-specific scenarios
-- =============================================================================

SET search_path TO sim, public;

ALTER TABLE sim.escenario_predefinido
    ADD COLUMN rubro_id BIGINT REFERENCES sim.rubro(id) ON DELETE RESTRICT;

-- Assign existing 4 escenarios to RETAIL_CONV
UPDATE sim.escenario_predefinido
SET rubro_id = (SELECT id FROM sim.rubro WHERE codigo = 'RETAIL_CONV');

ALTER TABLE sim.escenario_predefinido
    ALTER COLUMN rubro_id SET NOT NULL;

CREATE INDEX idx_escenario_rubro ON sim.escenario_predefinido(rubro_id);

-- DOWN
-- DROP INDEX IF EXISTS sim.idx_escenario_rubro;
-- ALTER TABLE sim.escenario_predefinido DROP COLUMN IF EXISTS rubro_id;
