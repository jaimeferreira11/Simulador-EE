-- =============================================================================
-- V202604271000__ia_coaching.sql
-- Agrega soporte para coaching IA por trimestre:
--   1. Columna ia_habilitada en sim.competencia
--   2. Tabla sim.coaching_trimestre para almacenar feedback generado por IA
-- =============================================================================

SET search_path TO sim, public;

-- 1. Agregar columna ia_habilitada a sim.competencia
ALTER TABLE sim.competencia
    ADD COLUMN ia_habilitada BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN sim.competencia.ia_habilitada IS 'Habilita generación de coaching IA al cierre de cada trimestre';

-- 2. Crear tabla coaching_trimestre
CREATE TABLE sim.coaching_trimestre (
    id              BIGSERIAL       NOT NULL,
    trimestre_id    BIGINT          NOT NULL,
    equipo_id       BIGINT          NOT NULL,
    texto           TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_coaching_trimestre            PRIMARY KEY (id),
    CONSTRAINT fk_ct_trimestre                  FOREIGN KEY (trimestre_id)
                                                    REFERENCES sim.trimestre(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ct_equipo                     FOREIGN KEY (equipo_id)
                                                    REFERENCES sim.equipo(id) ON DELETE RESTRICT,
    CONSTRAINT uk_ct_trimestre_equipo           UNIQUE (trimestre_id, equipo_id)
);

COMMENT ON TABLE  sim.coaching_trimestre IS 'Feedback de coaching generado por IA al cierre de cada trimestre, uno por equipo';
COMMENT ON COLUMN sim.coaching_trimestre.texto IS 'Texto de coaching en español generado por IA, no prescriptivo';

CREATE INDEX idx_coaching_trimestre_trimestre_id ON sim.coaching_trimestre (trimestre_id);

-- DOWN (rollback reference)
-- DROP TABLE sim.coaching_trimestre;
-- ALTER TABLE sim.competencia DROP COLUMN ia_habilitada;
