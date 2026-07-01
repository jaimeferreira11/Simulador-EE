-- up
CREATE TABLE sim.password_reset_token (
    id              BIGSERIAL       PRIMARY KEY,
    usuario_id      BIGINT          NOT NULL REFERENCES sim.usuario(id) ON DELETE RESTRICT,
    token           VARCHAR(64)     NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ     NOT NULL,
    used            BOOLEAN         DEFAULT FALSE,
    created_at      TIMESTAMPTZ     DEFAULT NOW()
);

CREATE INDEX idx_password_reset_token_usuario ON sim.password_reset_token(usuario_id);
CREATE INDEX idx_password_reset_token_token ON sim.password_reset_token(token);

-- down
-- DROP TABLE sim.password_reset_token;
