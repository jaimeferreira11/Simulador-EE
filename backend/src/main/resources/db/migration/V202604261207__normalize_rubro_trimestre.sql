-- =============================================================================
-- V202604261207__normalize_rubro_trimestre.sql
-- Normaliza estacionalidad del rubro: de columnas fijas (_q1.._q4)
-- a tabla hija parametro_rubro_trimestre con filas por trimestre.
-- Permite estacionalidad personalizada para competencias >4 trimestres.
-- =============================================================================

SET search_path TO sim, public;

-- 1. Crear tabla hija
CREATE TABLE sim.parametro_rubro_trimestre (
    id              BIGSERIAL       NOT NULL,
    rubro_param_id  BIGINT          NOT NULL,
    trimestre       INT             NOT NULL,  -- 1, 2, 3, 4, ... N
    estacionalidad  NUMERIC(5,4)    NOT NULL DEFAULT 1.0000,

    CONSTRAINT pk_parametro_rubro_trimestre          PRIMARY KEY (id),
    CONSTRAINT fk_prt_rubro_param                    FOREIGN KEY (rubro_param_id)
                                                        REFERENCES sim.parametro_rubro(id) ON DELETE CASCADE,
    CONSTRAINT uk_prt_rubro_param_trimestre           UNIQUE (rubro_param_id, trimestre),
    CONSTRAINT chk_prt_trimestre_positivo             CHECK (trimestre >= 1),
    CONSTRAINT chk_prt_estacionalidad                 CHECK (estacionalidad BETWEEN 0.5 AND 2.0)
);

COMMENT ON TABLE  sim.parametro_rubro_trimestre IS 'Estacionalidad de demanda por trimestre y rubro. Una fila por Q.';
COMMENT ON COLUMN sim.parametro_rubro_trimestre.estacionalidad IS 'Multiplicador de demanda (1.0=neutro, 1.18=pico navideño)';

-- 2. Migrar datos existentes: 4 filas por parametro_rubro
INSERT INTO sim.parametro_rubro_trimestre (rubro_param_id, trimestre, estacionalidad)
SELECT id, 1, estacionalidad_q1 FROM sim.parametro_rubro
UNION ALL
SELECT id, 2, estacionalidad_q2 FROM sim.parametro_rubro
UNION ALL
SELECT id, 3, estacionalidad_q3 FROM sim.parametro_rubro
UNION ALL
SELECT id, 4, estacionalidad_q4 FROM sim.parametro_rubro;

-- 3. Eliminar columnas Q del padre
ALTER TABLE sim.parametro_rubro
    DROP CONSTRAINT IF EXISTS chk_pr_estac_q1,
    DROP CONSTRAINT IF EXISTS chk_pr_estac_q2,
    DROP CONSTRAINT IF EXISTS chk_pr_estac_q3,
    DROP CONSTRAINT IF EXISTS chk_pr_estac_q4;

ALTER TABLE sim.parametro_rubro
    DROP COLUMN estacionalidad_q1,
    DROP COLUMN estacionalidad_q2,
    DROP COLUMN estacionalidad_q3,
    DROP COLUMN estacionalidad_q4;

-- DOWN (rollback reference)
-- DROP TABLE sim.parametro_rubro_trimestre;
-- ALTER TABLE sim.parametro_rubro ADD COLUMN estacionalidad_q1 NUMERIC(5,4) DEFAULT 1.0, ...
