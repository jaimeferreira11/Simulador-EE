-- =============================================================================
-- V202604211001__identidad_acceso.sql
-- Dominio: Identidad y Acceso
-- Tablas: rol_usuario, usuario
-- =============================================================================

SET search_path TO sim, public;

-- -----------------------------------------------------------------------------
-- rol_usuario: Catalogo de roles (ADMIN_PLATAFORMA, MODERADOR, JUGADOR)
-- -----------------------------------------------------------------------------
CREATE TABLE sim.rol_usuario (
    id              BIGSERIAL       NOT NULL,
    codigo          VARCHAR(30)     NOT NULL,
    nombre          VARCHAR(60)     NOT NULL,
    descripcion     TEXT,

    CONSTRAINT pk_rol_usuario            PRIMARY KEY (id),
    CONSTRAINT uk_rol_usuario_codigo     UNIQUE (codigo)
);

COMMENT ON TABLE  sim.rol_usuario          IS 'Catalogo de roles del sistema';
COMMENT ON COLUMN sim.rol_usuario.codigo   IS 'Codigo tecnico: ADMIN_PLATAFORMA, MODERADOR, JUGADOR';

-- -----------------------------------------------------------------------------
-- usuario: Usuarios de la plataforma
-- -----------------------------------------------------------------------------
CREATE TABLE sim.usuario (
    id                  BIGSERIAL       NOT NULL,
    email               VARCHAR(255)    NOT NULL,
    password_hash       VARCHAR(255)    NOT NULL,
    nombre_completo     VARCHAR(150)    NOT NULL,
    rol_usuario_id      BIGINT          NOT NULL,
    activo              BOOLEAN         NOT NULL DEFAULT TRUE,
    email_verificado    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_login_at       TIMESTAMPTZ,

    CONSTRAINT pk_usuario                PRIMARY KEY (id),
    CONSTRAINT uk_usuario_email          UNIQUE (email),
    CONSTRAINT fk_usuario_rol            FOREIGN KEY (rol_usuario_id)
                                         REFERENCES sim.rol_usuario (id)
                                         ON DELETE RESTRICT
);

CREATE INDEX idx_usuario_rol ON sim.usuario (rol_usuario_id);

COMMENT ON TABLE  sim.usuario                   IS 'Usuarios de la plataforma (moderadores, jugadores, admins)';
COMMENT ON COLUMN sim.usuario.password_hash     IS 'Hash bcrypt/argon2 — nunca exponer via API';
COMMENT ON COLUMN sim.usuario.activo            IS 'Soft-delete: si FALSE no puede iniciar sesion';

-- DOWN
-- DROP TABLE IF EXISTS sim.usuario;
-- DROP TABLE IF EXISTS sim.rol_usuario;
