-- =============================================================================
-- V202604251002__entidad_id_required.sql
-- Hace entidad_id NOT NULL en competencia
-- Todas las competencias existentes ya tienen entidad_id asignado (seed)
-- =============================================================================

SET search_path TO sim, public;

ALTER TABLE sim.competencia
    ALTER COLUMN entidad_id SET NOT NULL;

-- DOWN
-- ALTER TABLE sim.competencia ALTER COLUMN entidad_id DROP NOT NULL;
