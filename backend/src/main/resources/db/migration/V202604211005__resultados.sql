-- =============================================================================
-- V202604211005__resultados.sql
-- Dominio: Resultados (snapshots denormalizados)
-- Tablas: snapshot_estado, resultado_calculo, ranking_trimestre
-- =============================================================================

SET search_path TO sim, public;

-- -----------------------------------------------------------------------------
-- snapshot_estado: Estado completo de un equipo en INICIO y CIERRE de cada Q
-- -----------------------------------------------------------------------------
CREATE TABLE sim.snapshot_estado (
    id                  BIGSERIAL       NOT NULL,
    equipo_id           BIGINT          NOT NULL,
    trimestre_id        BIGINT          NOT NULL,
    momento             VARCHAR(10)     NOT NULL,

    -- Estado financiero
    caja                BIGINT          NOT NULL,
    deuda               BIGINT          NOT NULL DEFAULT 0,
    patrimonio_neto     BIGINT          NOT NULL,
    valor_planta        BIGINT          NOT NULL,

    -- Estado operativo
    capacidad           BIGINT          NOT NULL,
    headcount           SMALLINT        NOT NULL,
    salario             BIGINT          NOT NULL,
    inventario          BIGINT          NOT NULL,

    -- Estado de marca y calidad
    brand_equity        NUMERIC(5,2)    NOT NULL,
    calidad_percibida   NUMERIC(5,2)    NOT NULL,
    id_acumulado        BIGINT          NOT NULL DEFAULT 0,

    -- Performance
    pip                 NUMERIC(6,2)    NOT NULL,

    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_snapshot_estado                 PRIMARY KEY (id),
    CONSTRAINT uk_snapshot_equipo_trim_momento    UNIQUE (equipo_id, trimestre_id, momento),
    CONSTRAINT fk_snapshot_equipo                 FOREIGN KEY (equipo_id)
                                                  REFERENCES sim.equipo (id)
                                                  ON DELETE RESTRICT,
    CONSTRAINT fk_snapshot_trimestre              FOREIGN KEY (trimestre_id)
                                                  REFERENCES sim.trimestre (id)
                                                  ON DELETE RESTRICT,

    -- Validaciones
    CONSTRAINT chk_snap_momento          CHECK (momento IN ('INICIO', 'CIERRE')),
    CONSTRAINT chk_snap_capacidad        CHECK (capacidad > 0),
    CONSTRAINT chk_snap_headcount        CHECK (headcount >= 0),
    CONSTRAINT chk_snap_salario          CHECK (salario > 0),
    CONSTRAINT chk_snap_inventario       CHECK (inventario >= 0),
    CONSTRAINT chk_snap_be              CHECK (brand_equity BETWEEN 0 AND 100),
    CONSTRAINT chk_snap_calidad          CHECK (calidad_percibida BETWEEN 0 AND 100),
    CONSTRAINT chk_snap_id_acum          CHECK (id_acumulado >= 0),
    CONSTRAINT chk_snap_deuda            CHECK (deuda >= 0),
    CONSTRAINT chk_snap_valor_planta     CHECK (valor_planta >= 0)
);

-- Indices para consultas frecuentes del dashboard
CREATE INDEX idx_snapshot_equipo_q   ON sim.snapshot_estado (equipo_id, trimestre_id);
CREATE INDEX idx_snapshot_q_momento  ON sim.snapshot_estado (trimestre_id, momento);

COMMENT ON TABLE sim.snapshot_estado IS 'Estado completo denormalizado de un equipo por trimestre (INICIO + CIERRE)';
COMMENT ON COLUMN sim.snapshot_estado.momento IS 'INICIO=estado al abrir el Q, CIERRE=estado despues de procesar';
COMMENT ON COLUMN sim.snapshot_estado.caja IS 'Guaranies en caja (puede ser negativo en teoria, pero el motor lo previene)';

-- -----------------------------------------------------------------------------
-- resultado_calculo: Output completo del motor por equipo/trimestre
-- -----------------------------------------------------------------------------
CREATE TABLE sim.resultado_calculo (
    id                          BIGSERIAL       NOT NULL,
    equipo_id                   BIGINT          NOT NULL,
    trimestre_id                BIGINT          NOT NULL,

    -- Produccion
    utilizacion_capacidad       NUMERIC(5,4)    NOT NULL,
    factor_eficiencia           NUMERIC(5,4)    NOT NULL,
    produccion_real             BIGINT          NOT NULL,

    -- Mercado
    demanda_total_mercado       BIGINT          NOT NULL,
    demanda_asignada            BIGINT          NOT NULL,
    competitividad              NUMERIC(8,4)    NOT NULL,
    share                       NUMERIC(6,5)    NOT NULL,
    ventas_unidades             BIGINT          NOT NULL,

    -- Ingresos
    ingresos                    BIGINT          NOT NULL,

    -- Costos desglosados
    costo_mp_total              BIGINT          NOT NULL,
    costo_laboral               BIGINT          NOT NULL,
    costo_fijo                  BIGINT          NOT NULL,
    costo_marketing             BIGINT          NOT NULL,
    costo_id                    BIGINT          NOT NULL,
    costo_capacitacion          BIGINT          NOT NULL,
    costo_almacenamiento        BIGINT          NOT NULL,
    depreciacion                BIGINT          NOT NULL,
    intereses                   BIGINT          NOT NULL,
    costos_operativos_total     BIGINT          NOT NULL,

    -- Resultados
    utilidad_operativa          BIGINT          NOT NULL,
    utilidad_antes_impuestos    BIGINT          NOT NULL,
    impuesto_ire                BIGINT          NOT NULL,
    utilidad_neta               BIGINT          NOT NULL,
    pip_trimestre               NUMERIC(6,2)    NOT NULL,

    calculado_at                TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_resultado_calculo              PRIMARY KEY (id),
    CONSTRAINT uk_resultado_equipo_trim          UNIQUE (equipo_id, trimestre_id),
    CONSTRAINT fk_resultado_equipo               FOREIGN KEY (equipo_id)
                                                 REFERENCES sim.equipo (id)
                                                 ON DELETE RESTRICT,
    CONSTRAINT fk_resultado_trimestre            FOREIGN KEY (trimestre_id)
                                                 REFERENCES sim.trimestre (id)
                                                 ON DELETE RESTRICT,

    -- Validaciones
    CONSTRAINT chk_res_utilizacion       CHECK (utilizacion_capacidad BETWEEN 0 AND 2),
    CONSTRAINT chk_res_eficiencia        CHECK (factor_eficiencia > 0),
    CONSTRAINT chk_res_produccion        CHECK (produccion_real >= 0),
    CONSTRAINT chk_res_demanda_total     CHECK (demanda_total_mercado >= 0),
    CONSTRAINT chk_res_demanda_asig      CHECK (demanda_asignada >= 0),
    CONSTRAINT chk_res_competitividad    CHECK (competitividad >= 0),
    CONSTRAINT chk_res_share             CHECK (share BETWEEN 0 AND 1),
    CONSTRAINT chk_res_ventas            CHECK (ventas_unidades >= 0),
    CONSTRAINT chk_res_ingresos          CHECK (ingresos >= 0),
    CONSTRAINT chk_res_costo_mp          CHECK (costo_mp_total >= 0),
    CONSTRAINT chk_res_costo_laboral     CHECK (costo_laboral >= 0),
    CONSTRAINT chk_res_costo_fijo        CHECK (costo_fijo >= 0),
    CONSTRAINT chk_res_costo_mkt         CHECK (costo_marketing >= 0),
    CONSTRAINT chk_res_costo_id          CHECK (costo_id >= 0),
    CONSTRAINT chk_res_costo_cap         CHECK (costo_capacitacion >= 0),
    CONSTRAINT chk_res_costo_almac       CHECK (costo_almacenamiento >= 0),
    CONSTRAINT chk_res_depreciacion      CHECK (depreciacion >= 0),
    CONSTRAINT chk_res_intereses         CHECK (intereses >= 0),
    CONSTRAINT chk_res_costos_total      CHECK (costos_operativos_total >= 0),
    CONSTRAINT chk_res_ire               CHECK (impuesto_ire >= 0),
    CONSTRAINT chk_res_pip               CHECK (pip_trimestre BETWEEN 0 AND 100)
);

COMMENT ON TABLE sim.resultado_calculo IS 'Output completo del motor de simulacion por equipo/trimestre';
COMMENT ON COLUMN sim.resultado_calculo.competitividad IS 'Score de competitividad calculado con formula ponderada';
COMMENT ON COLUMN sim.resultado_calculo.pip_trimestre IS 'Performance Index Points del trimestre';

-- -----------------------------------------------------------------------------
-- ranking_trimestre: Posiciones al cierre de cada Q
-- -----------------------------------------------------------------------------
CREATE TABLE sim.ranking_trimestre (
    id                  BIGSERIAL       NOT NULL,
    competencia_id      BIGINT          NOT NULL,
    trimestre_id        BIGINT          NOT NULL,
    equipo_id           BIGINT          NOT NULL,
    posicion            SMALLINT        NOT NULL,
    pip_acumulado       NUMERIC(6,2)    NOT NULL,
    utilidad_acumulada  BIGINT          NOT NULL,
    caja_actual         BIGINT          NOT NULL,
    share_actual        NUMERIC(6,5)    NOT NULL,
    calculado_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_ranking_trimestre              PRIMARY KEY (id),
    CONSTRAINT uk_ranking_trim_equipo            UNIQUE (trimestre_id, equipo_id),
    CONSTRAINT uk_ranking_trim_posicion          UNIQUE (trimestre_id, posicion),
    CONSTRAINT fk_ranking_competencia            FOREIGN KEY (competencia_id)
                                                 REFERENCES sim.competencia (id)
                                                 ON DELETE RESTRICT,
    CONSTRAINT fk_ranking_trimestre              FOREIGN KEY (trimestre_id)
                                                 REFERENCES sim.trimestre (id)
                                                 ON DELETE RESTRICT,
    CONSTRAINT fk_ranking_equipo                 FOREIGN KEY (equipo_id)
                                                 REFERENCES sim.equipo (id)
                                                 ON DELETE RESTRICT,

    CONSTRAINT chk_rank_posicion         CHECK (posicion >= 1),
    CONSTRAINT chk_rank_share            CHECK (share_actual BETWEEN 0 AND 1)
);

CREATE INDEX idx_ranking_competencia ON sim.ranking_trimestre (competencia_id);

COMMENT ON TABLE sim.ranking_trimestre IS 'Rankings calculados al cierre de cada trimestre para evitar recalcular';

-- DOWN
-- DROP TABLE IF EXISTS sim.ranking_trimestre;
-- DROP TABLE IF EXISTS sim.resultado_calculo;
-- DROP TABLE IF EXISTS sim.snapshot_estado;
