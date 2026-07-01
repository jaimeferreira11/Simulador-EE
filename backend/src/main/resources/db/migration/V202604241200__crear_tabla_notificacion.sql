-- UP: tabla de notificaciones para jugadores
CREATE TABLE sim.notificacion (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT NOT NULL REFERENCES sim.usuario(id) ON DELETE RESTRICT,
    competencia_id  BIGINT REFERENCES sim.competencia(id) ON DELETE SET NULL,
    tipo            VARCHAR(40) NOT NULL,
    titulo          VARCHAR(200) NOT NULL,
    descripcion     TEXT,
    severidad       VARCHAR(10) NOT NULL CHECK (severidad IN ('INFO', 'IMPORTANTE', 'URGENTE')),
    leida           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    datos_extra     JSONB
);

CREATE INDEX idx_notif_usuario_leida ON sim.notificacion(usuario_id, leida);
CREATE INDEX idx_notif_usuario_created ON sim.notificacion(usuario_id, created_at DESC);

-- DOWN:
-- DROP TABLE sim.notificacion;
