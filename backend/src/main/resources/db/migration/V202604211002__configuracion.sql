-- =============================================================================
-- V202604211002__configuracion.sql
-- Dominio: Configuracion
-- Tablas: rubro, parametro_macro, parametro_rubro
-- =============================================================================

SET search_path TO sim, public;

-- -----------------------------------------------------------------------------
-- rubro: Catalogo de rubros/sectores (MVP: solo RETAIL_CONV)
-- -----------------------------------------------------------------------------
CREATE TABLE sim.rubro (
    id              BIGSERIAL       NOT NULL,
    codigo          VARCHAR(40)     NOT NULL,
    nombre          VARCHAR(120)    NOT NULL,
    descripcion     TEXT,
    activo          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_rubro                  PRIMARY KEY (id),
    CONSTRAINT uk_rubro_codigo           UNIQUE (codigo)
);

COMMENT ON TABLE sim.rubro IS 'Catalogo de rubros/sectores de mercado disponibles';

-- -----------------------------------------------------------------------------
-- parametro_macro: Sets de parametros macroeconomicos paraguayos
-- -----------------------------------------------------------------------------
CREATE TABLE sim.parametro_macro (
    id                  BIGSERIAL       NOT NULL,
    nombre_set          VARCHAR(80)     NOT NULL,
    vigente_desde       DATE            NOT NULL,

    -- Inflacion trimestral (fraccion decimal, ej: 0.012 = 1.2%)
    inflacion_trim_q1   NUMERIC(8,6)   NOT NULL,
    inflacion_trim_q2   NUMERIC(8,6)   NOT NULL,
    inflacion_trim_q3   NUMERIC(8,6)   NOT NULL,
    inflacion_trim_q4   NUMERIC(8,6)   NOT NULL,

    -- Tipo de cambio USD/PYG por trimestre
    tipo_cambio_q1      NUMERIC(12,2)  NOT NULL,
    tipo_cambio_q2      NUMERIC(12,2)  NOT NULL,
    tipo_cambio_q3      NUMERIC(12,2)  NOT NULL,
    tipo_cambio_q4      NUMERIC(12,2)  NOT NULL,

    -- Tasa de Politica Monetaria BCP (anual)
    tpm_anual_q1        NUMERIC(6,4)   NOT NULL,
    tpm_anual_q4        NUMERIC(6,4)   NOT NULL,

    -- Salario Minimo Vigente (guaranies)
    salario_minimo_q1   BIGINT         NOT NULL,
    salario_minimo_q4   BIGINT         NOT NULL,

    -- Cargas sociales y tributarias
    ips_patronal        NUMERIC(5,4)   NOT NULL DEFAULT 0.1650,
    ips_trabajador      NUMERIC(5,4)   NOT NULL DEFAULT 0.0900,
    aguinaldo_factor    NUMERIC(6,5)   NOT NULL DEFAULT 0.08333,
    tasa_ire            NUMERIC(5,4)   NOT NULL DEFAULT 0.1000,
    iva_general         NUMERIC(5,4)   NOT NULL DEFAULT 0.1000,

    activo              BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_parametro_macro                PRIMARY KEY (id),
    CONSTRAINT uk_parametro_macro_nombre_set     UNIQUE (nombre_set),

    -- Validaciones de rango
    CONSTRAINT chk_pm_inflacion_q1   CHECK (inflacion_trim_q1 >= 0),
    CONSTRAINT chk_pm_inflacion_q2   CHECK (inflacion_trim_q2 >= 0),
    CONSTRAINT chk_pm_inflacion_q3   CHECK (inflacion_trim_q3 >= 0),
    CONSTRAINT chk_pm_inflacion_q4   CHECK (inflacion_trim_q4 >= 0),
    CONSTRAINT chk_pm_tc_q1          CHECK (tipo_cambio_q1 > 0),
    CONSTRAINT chk_pm_tc_q2          CHECK (tipo_cambio_q2 > 0),
    CONSTRAINT chk_pm_tc_q3          CHECK (tipo_cambio_q3 > 0),
    CONSTRAINT chk_pm_tc_q4          CHECK (tipo_cambio_q4 > 0),
    CONSTRAINT chk_pm_tpm_q1         CHECK (tpm_anual_q1 >= 0),
    CONSTRAINT chk_pm_tpm_q4         CHECK (tpm_anual_q4 >= 0),
    CONSTRAINT chk_pm_smv_q1         CHECK (salario_minimo_q1 > 0),
    CONSTRAINT chk_pm_smv_q4         CHECK (salario_minimo_q4 > 0),
    CONSTRAINT chk_pm_ips_patronal   CHECK (ips_patronal BETWEEN 0 AND 1),
    CONSTRAINT chk_pm_ips_trabajador CHECK (ips_trabajador BETWEEN 0 AND 1),
    CONSTRAINT chk_pm_aguinaldo      CHECK (aguinaldo_factor BETWEEN 0 AND 1),
    CONSTRAINT chk_pm_ire            CHECK (tasa_ire BETWEEN 0 AND 1),
    CONSTRAINT chk_pm_iva            CHECK (iva_general BETWEEN 0 AND 1)
);

COMMENT ON TABLE sim.parametro_macro IS 'Sets versionados de parametros macroeconomicos paraguayos';
COMMENT ON COLUMN sim.parametro_macro.inflacion_trim_q1 IS 'Inflacion trimestral Q1 como fraccion decimal';
COMMENT ON COLUMN sim.parametro_macro.salario_minimo_q1 IS 'Salario Minimo Vigente Q1 en guaranies';

-- -----------------------------------------------------------------------------
-- parametro_rubro: Calibracion de parametros por rubro/sector
-- -----------------------------------------------------------------------------
CREATE TABLE sim.parametro_rubro (
    id                          BIGSERIAL       NOT NULL,
    rubro_id                    BIGINT          NOT NULL,
    codigo                      VARCHAR(60)     NOT NULL,

    -- Demanda y precio
    demanda_base_trim           BIGINT          NOT NULL,
    precio_referencia           BIGINT          NOT NULL,

    -- Elasticidades (exponentes de la formula de competitividad)
    elasticidad_precio          NUMERIC(4,3)    NOT NULL,
    elasticidad_marketing       NUMERIC(4,3)    NOT NULL,
    elasticidad_calidad         NUMERIC(4,3)    NOT NULL,

    -- Pesos de competitividad (deben sumar 1.0)
    peso_precio                 NUMERIC(4,3)    NOT NULL,
    peso_marketing              NUMERIC(4,3)    NOT NULL,
    peso_calidad                NUMERIC(4,3)    NOT NULL,
    peso_marca                  NUMERIC(4,3)    NOT NULL,

    -- Costos
    costo_unit_mp               BIGINT          NOT NULL,
    pct_mp_importada            NUMERIC(5,4)    NOT NULL,
    costos_fijos_trim           BIGINT          NOT NULL,
    depreciacion_trim           NUMERIC(5,4)    NOT NULL DEFAULT 0.0500,
    costo_expansion_capacidad   BIGINT          NOT NULL,

    -- Recursos humanos
    salario_promedio_sector     BIGINT          NOT NULL,
    productividad_empleado      BIGINT          NOT NULL,

    -- Brand equity
    brand_equity_inicial        NUMERIC(5,2)    NOT NULL DEFAULT 50.00,
    decaimiento_be              NUMERIC(5,4)    NOT NULL DEFAULT 0.0500,

    activo                      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_parametro_rubro                PRIMARY KEY (id),
    CONSTRAINT uk_parametro_rubro_codigo         UNIQUE (codigo),
    CONSTRAINT fk_parametro_rubro_rubro          FOREIGN KEY (rubro_id)
                                                 REFERENCES sim.rubro (id)
                                                 ON DELETE RESTRICT,

    -- Validaciones de rango
    CONSTRAINT chk_pr_demanda           CHECK (demanda_base_trim > 0),
    CONSTRAINT chk_pr_precio_ref        CHECK (precio_referencia > 0),
    CONSTRAINT chk_pr_elast_precio      CHECK (elasticidad_precio > 0),
    CONSTRAINT chk_pr_elast_mkt         CHECK (elasticidad_marketing >= 0),
    CONSTRAINT chk_pr_elast_cal         CHECK (elasticidad_calidad >= 0),
    CONSTRAINT chk_pr_peso_precio       CHECK (peso_precio BETWEEN 0 AND 1),
    CONSTRAINT chk_pr_peso_mkt          CHECK (peso_marketing BETWEEN 0 AND 1),
    CONSTRAINT chk_pr_peso_cal          CHECK (peso_calidad BETWEEN 0 AND 1),
    CONSTRAINT chk_pr_peso_marca        CHECK (peso_marca BETWEEN 0 AND 1),
    CONSTRAINT chk_pr_costo_mp          CHECK (costo_unit_mp > 0),
    CONSTRAINT chk_pr_pct_importada     CHECK (pct_mp_importada BETWEEN 0 AND 1),
    CONSTRAINT chk_pr_costos_fijos      CHECK (costos_fijos_trim >= 0),
    CONSTRAINT chk_pr_depreciacion      CHECK (depreciacion_trim BETWEEN 0 AND 1),
    CONSTRAINT chk_pr_costo_expansion   CHECK (costo_expansion_capacidad > 0),
    CONSTRAINT chk_pr_salario_sector    CHECK (salario_promedio_sector > 0),
    CONSTRAINT chk_pr_productividad     CHECK (productividad_empleado > 0),
    CONSTRAINT chk_pr_be_inicial        CHECK (brand_equity_inicial BETWEEN 0 AND 100),
    CONSTRAINT chk_pr_decaimiento_be    CHECK (decaimiento_be BETWEEN 0 AND 1),

    -- Los 4 pesos deben sumar 1.0 (tolerancia 0.001)
    CONSTRAINT chk_pesos_suman_uno
        CHECK (ABS(peso_precio + peso_marketing + peso_calidad + peso_marca - 1.0) < 0.001)
);

CREATE INDEX idx_parametro_rubro_rubro ON sim.parametro_rubro (rubro_id);

COMMENT ON TABLE sim.parametro_rubro IS 'Calibracion de parametros del motor por rubro/sector';
COMMENT ON COLUMN sim.parametro_rubro.peso_precio IS 'Peso w_precio en formula de competitividad (retail default: 0.40)';

-- DOWN
-- DROP TABLE IF EXISTS sim.parametro_rubro;
-- DROP TABLE IF EXISTS sim.parametro_macro;
-- DROP TABLE IF EXISTS sim.rubro;
