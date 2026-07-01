-- =============================================================================
-- V202604261200__evento_catalogo_rubro_id.sql
-- Add optional rubro_id FK to evento_catalogo for sector-specific events
-- =============================================================================

SET search_path TO sim, public;

ALTER TABLE sim.evento_catalogo
    ADD COLUMN rubro_id BIGINT REFERENCES sim.rubro(id) ON DELETE RESTRICT;

CREATE INDEX idx_evento_catalogo_rubro ON sim.evento_catalogo(rubro_id);

-- Existing 12 events remain with rubro_id = NULL (global, apply to all rubros)

-- DOWN
-- DROP INDEX IF EXISTS sim.idx_evento_catalogo_rubro;
-- ALTER TABLE sim.evento_catalogo DROP COLUMN IF EXISTS rubro_id;
