-- Chat de equipo por competencia
CREATE TABLE sim.chat_mensaje (
    id             BIGSERIAL      PRIMARY KEY,
    competencia_id BIGINT         NOT NULL REFERENCES sim.competencia(id) ON DELETE RESTRICT,
    equipo_id      BIGINT         NOT NULL REFERENCES sim.equipo(id) ON DELETE RESTRICT,
    usuario_id     BIGINT         NOT NULL REFERENCES sim.usuario(id) ON DELETE RESTRICT,
    contenido      VARCHAR(1000)  NOT NULL CHECK (LENGTH(TRIM(contenido)) > 0),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_mensaje_equipo_comp ON sim.chat_mensaje (equipo_id, competencia_id, created_at DESC);
