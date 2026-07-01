-- =============================================================================
-- V202604211004__trimestre.sql
-- Dominio: Trimestre
-- Tablas: trimestre, decision_equipo
-- =============================================================================

SET search_path TO sim, public;

-- -----------------------------------------------------------------------------
-- trimestre: Cada turno de la competencia (ancla temporal del modelo)
-- -----------------------------------------------------------------------------
CREATE TABLE sim.trimestre (
    id                  BIGSERIAL       NOT NULL,
    competencia_id      BIGINT          NOT NULL,
    numero              SMALLINT        NOT NULL,
    estado              VARCHAR(20)     NOT NULL DEFAULT 'PENDIENTE',
    apertura_at         TIMESTAMPTZ,
    cierre_at           TIMESTAMPTZ,
    procesado_at        TIMESTAMPTZ,

    CONSTRAINT pk_trimestre                      PRIMARY KEY (id),
    CONSTRAINT uk_trimestre_comp_num             UNIQUE (competencia_id, numero),
    CONSTRAINT fk_trimestre_competencia          FOREIGN KEY (competencia_id)
                                                 REFERENCES sim.competencia (id)
                                                 ON DELETE RESTRICT,

    CONSTRAINT chk_trim_numero           CHECK (numero >= 0),
    CONSTRAINT chk_trim_estado           CHECK (estado IN (
        'PENDIENTE', 'ABIERTO_DECISIONES', 'CERRADO_PROCESANDO', 'PROCESADO', 'ANULADO'
    ))
);

CREATE INDEX idx_trimestre_competencia ON sim.trimestre (competencia_id);

COMMENT ON TABLE sim.trimestre IS 'Turnos de la competencia: 0=inicial, 1..N=jugables';
COMMENT ON COLUMN sim.trimestre.numero IS '0=trimestre inicial (Q0, solo snapshot), 1..N=turnos con decisiones';
COMMENT ON COLUMN sim.trimestre.estado IS 'PENDIENTE -> ABIERTO_DECISIONES -> CERRADO_PROCESANDO -> PROCESADO';

-- -----------------------------------------------------------------------------
-- decision_equipo: Decisiones de un equipo en un trimestre (1 fila por equipo/Q)
-- -----------------------------------------------------------------------------
CREATE TABLE sim.decision_equipo (
    id                          BIGSERIAL       NOT NULL,
    equipo_id                   BIGINT          NOT NULL,
    trimestre_id                BIGINT          NOT NULL,
    registrado_por_usuario_id   BIGINT          NOT NULL,

    -- Decisiones financieras
    prestamo_solicitado         BIGINT          NOT NULL DEFAULT 0,
    dividendos_pagar            BIGINT          NOT NULL DEFAULT 0,

    -- Decisiones operativas
    produccion_planificada      BIGINT          NOT NULL DEFAULT 0,
    compra_mp                   BIGINT          NOT NULL DEFAULT 0,
    inversion_capacidad         BIGINT          NOT NULL DEFAULT 0,

    -- Decisiones comerciales
    precio_venta                BIGINT          NOT NULL,
    inversion_marketing         BIGINT          NOT NULL DEFAULT 0,

    -- Decisiones de RRHH
    contrataciones_netas        SMALLINT        NOT NULL DEFAULT 0,
    aumento_salarial_pct        NUMERIC(5,4)    NOT NULL DEFAULT 0,
    inversion_capacitacion      BIGINT          NOT NULL DEFAULT 0,

    -- Decisiones de I+D
    inversion_id                BIGINT          NOT NULL DEFAULT 0,

    -- Estado de la decision
    estado                      VARCHAR(20)     NOT NULL DEFAULT 'BORRADOR',
    submitted_at                TIMESTAMPTZ,
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_decision_equipo                PRIMARY KEY (id),
    CONSTRAINT uk_decision_equipo_trim           UNIQUE (equipo_id, trimestre_id),
    CONSTRAINT fk_decision_equipo                FOREIGN KEY (equipo_id)
                                                 REFERENCES sim.equipo (id)
                                                 ON DELETE RESTRICT,
    CONSTRAINT fk_decision_trimestre             FOREIGN KEY (trimestre_id)
                                                 REFERENCES sim.trimestre (id)
                                                 ON DELETE RESTRICT,
    CONSTRAINT fk_decision_usuario               FOREIGN KEY (registrado_por_usuario_id)
                                                 REFERENCES sim.usuario (id)
                                                 ON DELETE RESTRICT,

    -- Validaciones
    CONSTRAINT chk_dec_prestamo          CHECK (prestamo_solicitado >= 0),
    CONSTRAINT chk_dec_dividendos        CHECK (dividendos_pagar >= 0),
    CONSTRAINT chk_dec_produccion        CHECK (produccion_planificada >= 0),
    CONSTRAINT chk_dec_compra_mp         CHECK (compra_mp >= 0),
    CONSTRAINT chk_dec_inv_capacidad     CHECK (inversion_capacidad >= 0),
    CONSTRAINT chk_dec_precio            CHECK (precio_venta > 0),
    CONSTRAINT chk_dec_inv_mkt           CHECK (inversion_marketing >= 0),
    CONSTRAINT chk_dec_aum_salarial      CHECK (aumento_salarial_pct >= 0),
    CONSTRAINT chk_dec_inv_capacitacion  CHECK (inversion_capacitacion >= 0),
    CONSTRAINT chk_dec_inv_id            CHECK (inversion_id >= 0),
    CONSTRAINT chk_dec_estado            CHECK (estado IN ('BORRADOR', 'ENVIADA', 'PROCESADA'))
);

CREATE INDEX idx_decision_trimestre ON sim.decision_equipo (trimestre_id);

COMMENT ON TABLE sim.decision_equipo IS 'Decisiones de un equipo en un trimestre — un registro por (equipo, trimestre)';
COMMENT ON COLUMN sim.decision_equipo.contrataciones_netas IS 'Puede ser negativo (despidos)';
COMMENT ON COLUMN sim.decision_equipo.estado IS 'BORRADOR -> ENVIADA -> PROCESADA';

-- DOWN
-- DROP TABLE IF EXISTS sim.decision_equipo;
-- DROP TABLE IF EXISTS sim.trimestre;
