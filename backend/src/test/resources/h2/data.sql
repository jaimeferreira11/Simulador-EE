-- =============================================================================
-- H2 minimal seed for tests using H2IntegrationTestBase.
-- Add only what's required by ported tests. Keep this file SHORT — anything
-- non-trivial probably belongs in a Postgres-backed test instead.
-- =============================================================================

INSERT INTO sim.entidad (nombre, tipo, activa)
VALUES ('Entidad Demo H2', 'UNIVERSIDAD', TRUE);
