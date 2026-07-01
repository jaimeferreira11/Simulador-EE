-- =============================================================================
-- V202604211003__competencia.sql
-- Dominio: Competencia
-- Tablas: competencia, equipo, equipo_miembro, evento_catalogo, evento_competencia
-- =============================================================================

SET search_path TO sim, public;

-- -----------------------------------------------------------------------------
-- competencia: Sesion de juego configurada por un moderador
-- -----------------------------------------------------------------------------
CREATE TABLE sim.competencia (
    id                      BIGSERIAL       NOT NULL,
    codigo                  VARCHAR(20)     NOT NULL,
    nombre                  VARCHAR(150)    NOT NULL,
    rubro_id                BIGINT          NOT NULL,
    parametro_macro_id      BIGINT          NOT NULL,
    parametro_rubro_id      BIGINT          NOT NULL,
    moderador_id            BIGINT          NOT NULL,

    -- Configuracion de la competencia
    num_trimestres          SMALLINT        NOT NULL DEFAULT 4,
    num_equipos_max         SMALLINT        NOT NULL DEFAULT 8,

    -- Condiciones iniciales (Q0) para todos los equipos
    caja_inicial            BIGINT          NOT NULL,
    capacidad_inicial       BIGINT          NOT NULL,
    headcount_inicial       SMALLINT        NOT NULL,
    salario_inicial         BIGINT          NOT NULL,
    inventario_inicial      BIGINT          NOT NULL DEFAULT 0,
    valor_planta_inicial    BIGINT          NOT NULL,

    -- Maquina de estados
    estado                  VARCHAR(20)     NOT NULL DEFAULT 'BORRADOR',

    -- Timestamps del ciclo de vida
    inicio_at               TIMESTAMPTZ,
    cierre_at               TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_competencia                    PRIMARY KEY (id),
    CONSTRAINT uk_competencia_codigo             UNIQUE (codigo),
    CONSTRAINT fk_competencia_rubro              FOREIGN KEY (rubro_id)
                                                 REFERENCES sim.rubro (id)
                                                 ON DELETE RESTRICT,
    CONSTRAINT fk_competencia_macro              FOREIGN KEY (parametro_macro_id)
                                                 REFERENCES sim.parametro_macro (id)
                                                 ON DELETE RESTRICT,
    CONSTRAINT fk_competencia_param_rubro        FOREIGN KEY (parametro_rubro_id)
                                                 REFERENCES sim.parametro_rubro (id)
                                                 ON DELETE RESTRICT,
    CONSTRAINT fk_competencia_moderador          FOREIGN KEY (moderador_id)
                                                 REFERENCES sim.usuario (id)
                                                 ON DELETE RESTRICT,

    -- Validaciones
    CONSTRAINT chk_comp_num_trim         CHECK (num_trimestres BETWEEN 4 AND 8),
    CONSTRAINT chk_comp_num_equipos      CHECK (num_equipos_max BETWEEN 2 AND 12),
    CONSTRAINT chk_comp_caja             CHECK (caja_inicial > 0),
    CONSTRAINT chk_comp_capacidad        CHECK (capacidad_inicial > 0),
    CONSTRAINT chk_comp_headcount        CHECK (headcount_inicial > 0),
    CONSTRAINT chk_comp_salario          CHECK (salario_inicial > 0),
    CONSTRAINT chk_comp_inventario       CHECK (inventario_inicial >= 0),
    CONSTRAINT chk_comp_valor_planta     CHECK (valor_planta_inicial > 0),
    CONSTRAINT chk_comp_estado           CHECK (estado IN (
        'BORRADOR', 'ABIERTA_INSCRIPCION', 'EN_CURSO', 'PAUSADA', 'FINALIZADA', 'ARCHIVADA'
    ))
);

CREATE INDEX idx_competencia_moderador ON sim.competencia (moderador_id);
CREATE INDEX idx_competencia_estado    ON sim.competencia (estado);

COMMENT ON TABLE sim.competencia IS 'Sesion de juego: agrupa equipos, trimestres y eventos';
COMMENT ON COLUMN sim.competencia.codigo IS 'Codigo corto para identificar la competencia (ej: RTL-AB12)';
COMMENT ON COLUMN sim.competencia.estado IS 'BORRADOR -> ABIERTA_INSCRIPCION -> EN_CURSO <-> PAUSADA -> FINALIZADA -> ARCHIVADA';

-- -----------------------------------------------------------------------------
-- equipo: Empresa simulada dentro de una competencia
-- -----------------------------------------------------------------------------
CREATE TABLE sim.equipo (
    id                  BIGSERIAL       NOT NULL,
    competencia_id      BIGINT          NOT NULL,
    nombre_empresa      VARCHAR(80)     NOT NULL,
    codigo_color        VARCHAR(7)      NOT NULL,
    estado              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVO',
    posicion_final      SMALLINT,
    pip_final           NUMERIC(6,2),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_equipo                         PRIMARY KEY (id),
    CONSTRAINT uk_equipo_nombre_comp             UNIQUE (competencia_id, nombre_empresa),
    CONSTRAINT fk_equipo_competencia             FOREIGN KEY (competencia_id)
                                                 REFERENCES sim.competencia (id)
                                                 ON DELETE RESTRICT,

    CONSTRAINT chk_equipo_color          CHECK (codigo_color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT chk_equipo_estado         CHECK (estado IN ('ACTIVO', 'INTERVENIDO', 'QUEBRADO', 'ELIMINADO')),
    CONSTRAINT chk_equipo_posicion       CHECK (posicion_final IS NULL OR posicion_final >= 1)
);

CREATE INDEX idx_equipo_competencia ON sim.equipo (competencia_id);

COMMENT ON TABLE sim.equipo IS 'Empresa simulada dentro de una competencia';
COMMENT ON COLUMN sim.equipo.codigo_color IS 'Color hex #RRGGBB para identificar al equipo en dashboards';

-- -----------------------------------------------------------------------------
-- equipo_miembro: Asociacion jugador <-> equipo
-- -----------------------------------------------------------------------------
CREATE TABLE sim.equipo_miembro (
    id              BIGSERIAL       NOT NULL,
    equipo_id       BIGINT          NOT NULL,
    usuario_id      BIGINT          NOT NULL,
    es_capitan      BOOLEAN         NOT NULL DEFAULT FALSE,
    joined_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_equipo_miembro                 PRIMARY KEY (id),
    CONSTRAINT uk_equipo_miembro                 UNIQUE (equipo_id, usuario_id),
    CONSTRAINT fk_equipo_miembro_equipo          FOREIGN KEY (equipo_id)
                                                 REFERENCES sim.equipo (id)
                                                 ON DELETE RESTRICT,
    CONSTRAINT fk_equipo_miembro_usuario         FOREIGN KEY (usuario_id)
                                                 REFERENCES sim.usuario (id)
                                                 ON DELETE RESTRICT
);

-- Solo un capitan por equipo (partial unique index)
CREATE UNIQUE INDEX idx_un_capitan
    ON sim.equipo_miembro (equipo_id)
    WHERE es_capitan = TRUE;

CREATE INDEX idx_equipo_miembro_usuario ON sim.equipo_miembro (usuario_id);

COMMENT ON TABLE sim.equipo_miembro IS 'Asociacion N:M entre jugadores y equipos';
COMMENT ON COLUMN sim.equipo_miembro.es_capitan IS 'El capitan es quien puede enviar decisiones definitivas';

-- -----------------------------------------------------------------------------
-- evento_catalogo: Catalogo de eventos disponibles
-- -----------------------------------------------------------------------------
CREATE TABLE sim.evento_catalogo (
    id                          BIGSERIAL       NOT NULL,
    codigo                      VARCHAR(60)     NOT NULL,
    nombre                      VARCHAR(150)    NOT NULL,
    descripcion                 TEXT            NOT NULL,
    severidad                   VARCHAR(20)     NOT NULL,
    tipo_efecto                 VARCHAR(40)     NOT NULL,
    magnitud_default            NUMERIC(6,4)    NOT NULL,
    duracion_q                  SMALLINT        NOT NULL DEFAULT 1,
    requiere_anuncio_previo     BOOLEAN         NOT NULL DEFAULT FALSE,
    activo                      BOOLEAN         NOT NULL DEFAULT TRUE,

    -- Overrides opcionales de pesos de competitividad durante el evento
    override_peso_precio        NUMERIC(4,3),
    override_peso_marketing     NUMERIC(4,3),
    override_peso_calidad       NUMERIC(4,3),
    override_peso_marca         NUMERIC(4,3),

    CONSTRAINT pk_evento_catalogo                PRIMARY KEY (id),
    CONSTRAINT uk_evento_catalogo_codigo         UNIQUE (codigo),

    CONSTRAINT chk_ec_severidad          CHECK (severidad IN ('LEVE', 'MODERADO', 'GRAVE', 'POSITIVO')),
    CONSTRAINT chk_ec_tipo_efecto        CHECK (tipo_efecto IN (
        'COSTO_LOGISTICO', 'COSTO_FIJO', 'COSTO_MP', 'DEMANDA_TOTAL', 'TASA_INTERES', 'TIPO_CAMBIO'
    )),
    CONSTRAINT chk_ec_duracion           CHECK (duracion_q >= 1),

    -- Overrides: o todos NULL, o todos NOT NULL y suman 1.0
    CONSTRAINT chk_override_pesos_completos CHECK (
        (override_peso_precio IS NULL AND override_peso_marketing IS NULL
         AND override_peso_calidad IS NULL AND override_peso_marca IS NULL)
        OR
        (override_peso_precio IS NOT NULL AND override_peso_marketing IS NOT NULL
         AND override_peso_calidad IS NOT NULL AND override_peso_marca IS NOT NULL
         AND ABS(override_peso_precio + override_peso_marketing
                 + override_peso_calidad + override_peso_marca - 1.0) < 0.001)
    ),
    CONSTRAINT chk_ec_ovr_precio         CHECK (override_peso_precio IS NULL
                                                 OR override_peso_precio BETWEEN 0 AND 1),
    CONSTRAINT chk_ec_ovr_mkt            CHECK (override_peso_marketing IS NULL
                                                 OR override_peso_marketing BETWEEN 0 AND 1),
    CONSTRAINT chk_ec_ovr_cal            CHECK (override_peso_calidad IS NULL
                                                 OR override_peso_calidad BETWEEN 0 AND 1),
    CONSTRAINT chk_ec_ovr_marca          CHECK (override_peso_marca IS NULL
                                                 OR override_peso_marca BETWEEN 0 AND 1)
);

COMMENT ON TABLE sim.evento_catalogo IS 'Catalogo de eventos disponibles para disparar en competencias';
COMMENT ON COLUMN sim.evento_catalogo.magnitud_default IS 'Delta por defecto (ej: 0.03 = +3%)';
COMMENT ON COLUMN sim.evento_catalogo.override_peso_precio IS 'Si definido, reemplaza peso_precio del rubro durante el evento';

-- NOTA: evento_competencia se crea en V202604211004b porque depende de sim.trimestre

-- DOWN
-- DROP TABLE IF EXISTS sim.evento_catalogo;
-- DROP TABLE IF EXISTS sim.equipo_miembro;
-- DROP TABLE IF EXISTS sim.equipo;
-- DROP TABLE IF EXISTS sim.competencia;
