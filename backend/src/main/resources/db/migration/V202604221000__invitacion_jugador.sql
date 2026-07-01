-- =============================================================================
-- V202604221000__invitacion_jugador.sql
-- Dominio: Invitación de jugadores por moderador
-- Tabla: invitacion
-- =============================================================================

SET search_path TO sim, public;

CREATE TABLE sim.invitacion (
    id                  BIGSERIAL       NOT NULL,
    equipo_id           BIGINT          NOT NULL,
    email               VARCHAR(255)    NOT NULL,
    nombre_completo     VARCHAR(150)    NOT NULL,
    token               VARCHAR(128)    NOT NULL,
    estado              VARCHAR(20)     NOT NULL DEFAULT 'PENDIENTE'
                        CHECK (estado IN ('PENDIENTE', 'ACEPTADA', 'EXPIRADA')),
    creada_por          BIGINT          NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ     NOT NULL,
    accepted_at         TIMESTAMPTZ,

    CONSTRAINT pk_invitacion            PRIMARY KEY (id),
    CONSTRAINT uk_invitacion_token      UNIQUE (token),
    CONSTRAINT fk_invitacion_equipo     FOREIGN KEY (equipo_id)
                                         REFERENCES sim.equipo (id)
                                         ON DELETE RESTRICT,
    CONSTRAINT fk_invitacion_creador    FOREIGN KEY (creada_por)
                                         REFERENCES sim.usuario (id)
                                         ON DELETE RESTRICT
);

CREATE INDEX idx_invitacion_equipo ON sim.invitacion (equipo_id);
CREATE INDEX idx_invitacion_email  ON sim.invitacion (email);
CREATE INDEX idx_invitacion_token  ON sim.invitacion (token);

COMMENT ON TABLE  sim.invitacion                IS 'Invitaciones de moderador a jugadores';
COMMENT ON COLUMN sim.invitacion.token          IS 'Token opaco para el link de invitación';
COMMENT ON COLUMN sim.invitacion.estado         IS 'PENDIENTE: enviada, ACEPTADA: jugador activó, EXPIRADA: caducó';

-- DOWN
-- DROP TABLE IF EXISTS sim.invitacion;
