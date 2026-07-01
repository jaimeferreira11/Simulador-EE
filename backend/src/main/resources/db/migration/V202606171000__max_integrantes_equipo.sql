-- =============================================================================
-- Capacidad de equipo configurable por competencia
-- -----------------------------------------------------------------------------
-- Agrega un maximo opcional de integrantes por equipo, definido a nivel de la
-- competencia. NULL = sin limite (comportamiento historico): las competencias
-- existentes quedan sin restriccion. El motor de simulacion y el Golden File NO
-- se ven afectados: esta columna solo gobierna cuantos jugadores pueden ser
-- invitados/aceptados en un equipo.
--
-- La cuenta efectiva (miembros actuales + invitaciones PENDIENTES) se valida en
-- el servicio de invitaciones; aqui solo se garantiza un valor coherente (>= 1).
-- =============================================================================

ALTER TABLE sim.competencia
    ADD COLUMN max_integrantes_equipo SMALLINT
        CONSTRAINT competencia_max_integrantes_equipo_chk
        CHECK (max_integrantes_equipo IS NULL OR max_integrantes_equipo >= 1);

COMMENT ON COLUMN sim.competencia.max_integrantes_equipo IS
    'Maximo de integrantes por equipo (miembros + invitaciones pendientes). NULL = sin limite.';
