-- =============================================================================
-- V202604211008__refresh_token.sql
-- Dominio: Identidad y Acceso
-- Tabla: refresh_token (tokens de refresco para autenticacion JWT)
-- =============================================================================

SET search_path TO sim, public;

CREATE TABLE sim.refresh_token (
    id              BIGSERIAL       NOT NULL,
    usuario_id      BIGINT          NOT NULL,
    token_hash      VARCHAR(255)    NOT NULL,
    expires_at      TIMESTAMPTZ     NOT NULL,
    revocado        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_refresh_token          PRIMARY KEY (id),
    CONSTRAINT fk_refresh_token_usuario  FOREIGN KEY (usuario_id)
                                         REFERENCES sim.usuario (id)
                                         ON DELETE CASCADE
);

CREATE INDEX idx_refresh_token_usuario ON sim.refresh_token (usuario_id);
CREATE INDEX idx_refresh_token_hash    ON sim.refresh_token (token_hash);

COMMENT ON TABLE  sim.refresh_token             IS 'Tokens de refresco para autenticacion JWT';
COMMENT ON COLUMN sim.refresh_token.token_hash  IS 'SHA-256 del token opaco — nunca se almacena en claro';
COMMENT ON COLUMN sim.refresh_token.revocado    IS 'TRUE si fue rotado o invalidado por logout';

-- DOWN
-- DROP TABLE IF EXISTS sim.refresh_token;
