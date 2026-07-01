-- =============================================================================
-- V202604211009__parametro_rubro_estacionalidad_spread.sql
-- Agrega estacionalidad por trimestre y spread de tasa activa al rubro.
-- Antes estaban hardcodeados en el motor de simulacion.
-- =============================================================================

SET search_path TO sim, public;

-- Estacionalidad: multiplicador de demanda por trimestre (ej: Q4=1.18 para retail)
ALTER TABLE sim.parametro_rubro
    ADD COLUMN estacionalidad_q1 NUMERIC(5,4) NOT NULL DEFAULT 1.0000,
    ADD COLUMN estacionalidad_q2 NUMERIC(5,4) NOT NULL DEFAULT 1.0000,
    ADD COLUMN estacionalidad_q3 NUMERIC(5,4) NOT NULL DEFAULT 1.0000,
    ADD COLUMN estacionalidad_q4 NUMERIC(5,4) NOT NULL DEFAULT 1.0000;

-- Spread sobre TPM para calcular tasa activa: tasa_trim = (TPM + spread) / 4
ALTER TABLE sim.parametro_rubro
    ADD COLUMN spread_tasa NUMERIC(5,4) NOT NULL DEFAULT 0.0850;

-- Constraints de rango
ALTER TABLE sim.parametro_rubro
    ADD CONSTRAINT chk_pr_estac_q1 CHECK (estacionalidad_q1 BETWEEN 0.5 AND 2.0),
    ADD CONSTRAINT chk_pr_estac_q2 CHECK (estacionalidad_q2 BETWEEN 0.5 AND 2.0),
    ADD CONSTRAINT chk_pr_estac_q3 CHECK (estacionalidad_q3 BETWEEN 0.5 AND 2.0),
    ADD CONSTRAINT chk_pr_estac_q4 CHECK (estacionalidad_q4 BETWEEN 0.5 AND 2.0),
    ADD CONSTRAINT chk_pr_spread   CHECK (spread_tasa BETWEEN 0 AND 0.5);

COMMENT ON COLUMN sim.parametro_rubro.estacionalidad_q1 IS 'Multiplicador de demanda Q1 (retail: 0.95 = baja estacional)';
COMMENT ON COLUMN sim.parametro_rubro.estacionalidad_q4 IS 'Multiplicador de demanda Q4 (retail: 1.18 = pico navidad)';
COMMENT ON COLUMN sim.parametro_rubro.spread_tasa IS 'Spread sobre TPM para tasa activa (default 8.5%)';

-- Actualizar seed del rubro retail con valores del Golden File
UPDATE sim.parametro_rubro
SET estacionalidad_q1 = 0.9500,
    estacionalidad_q2 = 1.0000,
    estacionalidad_q3 = 1.0500,
    estacionalidad_q4 = 1.1800,
    spread_tasa       = 0.0850
WHERE codigo = 'RETAIL_CONV_BASE_2026';

-- DOWN
-- ALTER TABLE sim.parametro_rubro
--     DROP COLUMN estacionalidad_q1,
--     DROP COLUMN estacionalidad_q2,
--     DROP COLUMN estacionalidad_q3,
--     DROP COLUMN estacionalidad_q4,
--     DROP COLUMN spread_tasa;
