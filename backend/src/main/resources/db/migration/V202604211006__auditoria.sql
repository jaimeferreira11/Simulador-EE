-- =============================================================================
-- V202604211006__auditoria.sql
-- Dominio: Auditoria
-- Tablas: auditoria_decision, auditoria_evento
-- =============================================================================

SET search_path TO sim, public;

-- -----------------------------------------------------------------------------
-- auditoria_decision: Historial inmutable de cambios sobre decisiones
-- Append-only: nunca se actualizan ni borran registros
-- -----------------------------------------------------------------------------
CREATE TABLE sim.auditoria_decision (
    id                      BIGSERIAL       NOT NULL,
    decision_equipo_id      BIGINT          NOT NULL,
    usuario_id              BIGINT,
    accion                  VARCHAR(20)     NOT NULL,
    estado_anterior         JSONB,
    estado_nuevo            JSONB           NOT NULL,
    ip_origen               INET,
    ocurrido_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_auditoria_decision             PRIMARY KEY (id),
    CONSTRAINT fk_auddec_decision                FOREIGN KEY (decision_equipo_id)
                                                 REFERENCES sim.decision_equipo (id)
                                                 ON DELETE RESTRICT,
    CONSTRAINT fk_auddec_usuario                 FOREIGN KEY (usuario_id)
                                                 REFERENCES sim.usuario (id)
                                                 ON DELETE SET NULL,

    CONSTRAINT chk_auddec_accion         CHECK (accion IN ('CREADA', 'MODIFICADA', 'ENVIADA', 'ANULADA'))
);

-- Indice para consultar bitacora de un equipo (via decision_equipo)
CREATE INDEX idx_auddec_decision ON sim.auditoria_decision (decision_equipo_id);
CREATE INDEX idx_auddec_ocurrido ON sim.auditoria_decision (ocurrido_at);

COMMENT ON TABLE sim.auditoria_decision IS 'Historial inmutable (append-only) de cambios sobre decisiones de equipos';

-- -----------------------------------------------------------------------------
-- auditoria_evento: Bitacora general de acciones sobre competencias
-- Append-only: nunca se actualizan ni borran registros
-- -----------------------------------------------------------------------------
CREATE TABLE sim.auditoria_evento (
    id                  BIGSERIAL       NOT NULL,
    competencia_id      BIGINT          NOT NULL,
    usuario_id          BIGINT,
    tipo_accion         VARCHAR(40)     NOT NULL,
    descripcion         TEXT            NOT NULL,
    datos_contexto      JSONB,
    ip_origen           INET,
    ocurrido_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_auditoria_evento               PRIMARY KEY (id),
    CONSTRAINT fk_audev_competencia              FOREIGN KEY (competencia_id)
                                                 REFERENCES sim.competencia (id)
                                                 ON DELETE RESTRICT,
    CONSTRAINT fk_audev_usuario                  FOREIGN KEY (usuario_id)
                                                 REFERENCES sim.usuario (id)
                                                 ON DELETE SET NULL
);

-- Indices para consultas frecuentes de auditoria
CREATE INDEX idx_audev_competencia ON sim.auditoria_evento (competencia_id);
CREATE INDEX idx_audev_ocurrido    ON sim.auditoria_evento (ocurrido_at);
CREATE INDEX idx_audev_tipo        ON sim.auditoria_evento (tipo_accion);

COMMENT ON TABLE sim.auditoria_evento IS 'Bitacora general append-only de acciones sobre competencias';
COMMENT ON COLUMN sim.auditoria_evento.tipo_accion IS 'Ej: ABRE_TRIMESTRE, DISPARA_EVENTO, CIERRA_COMPETENCIA';

-- DOWN
-- DROP TABLE IF EXISTS sim.auditoria_evento;
-- DROP TABLE IF EXISTS sim.auditoria_decision;
