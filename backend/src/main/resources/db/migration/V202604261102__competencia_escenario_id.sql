-- =============================================================================
-- V202604261102__competencia_escenario_id.sql
-- Add escenario_id to competencia for scenario-based creation
-- =============================================================================

SET search_path TO sim, public;

ALTER TABLE sim.competencia
    ADD COLUMN escenario_id BIGINT REFERENCES sim.escenario_predefinido(id);

-- DOWN
-- ALTER TABLE sim.competencia DROP COLUMN escenario_id;
