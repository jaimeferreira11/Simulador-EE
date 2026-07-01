-- =============================================================================
-- V202604211000__create_schemas_and_extensions.sql
-- Schema principal y extensiones requeridas
-- =============================================================================

-- UP
CREATE SCHEMA IF NOT EXISTS sim;

COMMENT ON SCHEMA sim IS 'Schema principal del Simulador de Negocios';

-- Extensiones utilizadas
CREATE EXTENSION IF NOT EXISTS "pgcrypto";  -- gen_random_bytes, crypt, gen_salt

-- Configurar search_path para incluir sim
DO $$ BEGIN
  EXECUTE format('ALTER DATABASE %I SET search_path TO sim, public', current_database());
END $$;
SET search_path TO sim, public;

-- DOWN (rollback)
-- DROP SCHEMA IF EXISTS sim CASCADE;
-- DROP EXTENSION IF EXISTS "pgcrypto";
