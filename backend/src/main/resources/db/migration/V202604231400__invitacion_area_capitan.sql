-- V202604231400__invitacion_area_capitan.sql
-- Agrega area_id y es_capitan a la tabla invitacion para que se persistan
-- al momento de invitar y se apliquen cuando el jugador acepta.

ALTER TABLE sim.invitacion
    ADD COLUMN area_id BIGINT,
    ADD COLUMN es_capitan BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT fk_invitacion_area FOREIGN KEY (area_id)
        REFERENCES sim.area_decision (id)
        ON DELETE SET NULL;

COMMENT ON COLUMN sim.invitacion.area_id IS 'Area funcional pre-asignada por el moderador';
COMMENT ON COLUMN sim.invitacion.es_capitan IS 'Si el invitado sera capitan del equipo';

-- DOWN
-- ALTER TABLE sim.invitacion DROP COLUMN IF EXISTS es_capitan;
-- ALTER TABLE sim.invitacion DROP COLUMN IF EXISTS area_id;
