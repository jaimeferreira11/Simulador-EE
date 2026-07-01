-- =============================================================================
-- V202604261206__normalize_macro_trimestre.sql
-- Normaliza parámetros macro trimestrales: de columnas fijas (_q1.._q4)
-- a tabla hija parametro_macro_trimestre con filas por trimestre.
-- Permite escalabilidad ilimitada de trimestres (Q5, Q6, ... QN).
-- =============================================================================

SET search_path TO sim, public;

-- 1. Crear tabla hija
CREATE TABLE sim.parametro_macro_trimestre (
    id              BIGSERIAL       NOT NULL,
    macro_id        BIGINT          NOT NULL,
    trimestre       INT             NOT NULL,  -- 1, 2, 3, 4, ... N
    inflacion_trim  NUMERIC(8,6)    NOT NULL,
    tipo_cambio     NUMERIC(12,2)   NOT NULL,
    tpm_anual       NUMERIC(6,4)    NOT NULL,

    CONSTRAINT pk_parametro_macro_trimestre          PRIMARY KEY (id),
    CONSTRAINT fk_pmt_macro                         FOREIGN KEY (macro_id)
                                                        REFERENCES sim.parametro_macro(id) ON DELETE CASCADE,
    CONSTRAINT uk_pmt_macro_trimestre                UNIQUE (macro_id, trimestre),
    CONSTRAINT chk_pmt_trimestre_positivo            CHECK (trimestre >= 1),
    CONSTRAINT chk_pmt_inflacion                     CHECK (inflacion_trim >= 0),
    CONSTRAINT chk_pmt_tipo_cambio                   CHECK (tipo_cambio > 0),
    CONSTRAINT chk_pmt_tpm                           CHECK (tpm_anual >= 0)
);

COMMENT ON TABLE  sim.parametro_macro_trimestre IS 'Valores macro por trimestre (inflación, TC, TPM). Una fila por Q.';
COMMENT ON COLUMN sim.parametro_macro_trimestre.trimestre IS 'Número de trimestre (1-based). Soporta >4 para competencias extendidas.';

-- 2. Migrar datos existentes: 4 filas por parametro_macro
INSERT INTO sim.parametro_macro_trimestre (macro_id, trimestre, inflacion_trim, tipo_cambio, tpm_anual)
SELECT id, 1, inflacion_trim_q1, tipo_cambio_q1, tpm_anual_q1 FROM sim.parametro_macro
UNION ALL
SELECT id, 2, inflacion_trim_q2, tipo_cambio_q2, tpm_anual_q2 FROM sim.parametro_macro
UNION ALL
SELECT id, 3, inflacion_trim_q3, tipo_cambio_q3, tpm_anual_q3 FROM sim.parametro_macro
UNION ALL
SELECT id, 4, inflacion_trim_q4, tipo_cambio_q4, tpm_anual_q4 FROM sim.parametro_macro;

-- 3. Eliminar columnas Q del padre (ya no se usan)
ALTER TABLE sim.parametro_macro
    DROP CONSTRAINT IF EXISTS chk_pm_inflacion_q1,
    DROP CONSTRAINT IF EXISTS chk_pm_inflacion_q2,
    DROP CONSTRAINT IF EXISTS chk_pm_inflacion_q3,
    DROP CONSTRAINT IF EXISTS chk_pm_inflacion_q4,
    DROP CONSTRAINT IF EXISTS chk_pm_tc_q1,
    DROP CONSTRAINT IF EXISTS chk_pm_tc_q2,
    DROP CONSTRAINT IF EXISTS chk_pm_tc_q3,
    DROP CONSTRAINT IF EXISTS chk_pm_tc_q4,
    DROP CONSTRAINT IF EXISTS chk_pm_tpm_q1,
    DROP CONSTRAINT IF EXISTS chk_pm_tpm_q4,
    DROP CONSTRAINT IF EXISTS chk_pm_tpm_q2,
    DROP CONSTRAINT IF EXISTS chk_pm_tpm_q3;

ALTER TABLE sim.parametro_macro
    DROP COLUMN inflacion_trim_q1,
    DROP COLUMN inflacion_trim_q2,
    DROP COLUMN inflacion_trim_q3,
    DROP COLUMN inflacion_trim_q4,
    DROP COLUMN tipo_cambio_q1,
    DROP COLUMN tipo_cambio_q2,
    DROP COLUMN tipo_cambio_q3,
    DROP COLUMN tipo_cambio_q4,
    DROP COLUMN tpm_anual_q1,
    DROP COLUMN tpm_anual_q2,
    DROP COLUMN tpm_anual_q3,
    DROP COLUMN tpm_anual_q4;

-- DOWN (rollback reference)
-- DROP TABLE sim.parametro_macro_trimestre;
-- ALTER TABLE sim.parametro_macro ADD COLUMN inflacion_trim_q1 NUMERIC(8,6), ...
