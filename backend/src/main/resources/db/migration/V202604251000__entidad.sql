-- =============================================================================
-- V202604251000__entidad.sql
-- Dominio: Entidad
-- Tabla entidad: ente superior que agrupa competencias
-- (universidad, colegio, empresa, ONG, etc.)
-- FK entidad_id en competencia
-- =============================================================================

SET search_path TO sim, public;

-- -----------------------------------------------------------------------------
-- entidad: Ente que adquiere la plataforma y agrupa competencias
-- -----------------------------------------------------------------------------
CREATE TABLE sim.entidad (
    id              BIGSERIAL       NOT NULL,
    nombre          VARCHAR(200)    NOT NULL,
    tipo            VARCHAR(40)     NOT NULL,
    descripcion     TEXT,
    contacto_nombre VARCHAR(150),
    contacto_email  VARCHAR(200),
    activa          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_entidad          PRIMARY KEY (id),
    CONSTRAINT uk_entidad_nombre   UNIQUE (nombre),
    CONSTRAINT chk_entidad_tipo    CHECK (tipo IN (
        'UNIVERSIDAD', 'COLEGIO', 'EMPRESA', 'ONG', 'INSTITUTO', 'OTRO'
    ))
);

CREATE INDEX idx_entidad_tipo   ON sim.entidad (tipo);
CREATE INDEX idx_entidad_activa ON sim.entidad (activa);

COMMENT ON TABLE sim.entidad IS 'Ente superior (universidad, colegio, empresa, etc.) que agrupa competencias';
COMMENT ON COLUMN sim.entidad.tipo IS 'Tipo de entidad: UNIVERSIDAD, COLEGIO, EMPRESA, ONG, INSTITUTO, OTRO';

-- -----------------------------------------------------------------------------
-- Agregar FK entidad_id a competencia (nullable para migración gradual)
-- -----------------------------------------------------------------------------
ALTER TABLE sim.competencia
    ADD COLUMN entidad_id BIGINT;

ALTER TABLE sim.competencia
    ADD CONSTRAINT fk_competencia_entidad
    FOREIGN KEY (entidad_id) REFERENCES sim.entidad (id)
    ON DELETE RESTRICT;

CREATE INDEX idx_competencia_entidad ON sim.competencia (entidad_id);

COMMENT ON COLUMN sim.competencia.entidad_id IS 'Entidad a la que pertenece esta competencia';

-- DOWN
-- ALTER TABLE sim.competencia DROP CONSTRAINT fk_competencia_entidad;
-- ALTER TABLE sim.competencia DROP COLUMN entidad_id;
-- DROP TABLE IF EXISTS sim.entidad;
