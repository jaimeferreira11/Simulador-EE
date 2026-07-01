-- =============================================================================
-- V202604211004b__evento_competencia.sql
-- Dominio: Competencia (parte 2 — depende de sim.trimestre)
-- Tabla: evento_competencia
-- =============================================================================

SET search_path TO sim, public;

-- -----------------------------------------------------------------------------
-- evento_competencia: Instancia de evento aplicado a una competencia/trimestre
-- Separada porque tiene FK a sim.trimestre (creada en V...004)
-- -----------------------------------------------------------------------------
CREATE TABLE sim.evento_competencia (
    id                          BIGSERIAL       NOT NULL,
    competencia_id              BIGINT          NOT NULL,
    trimestre_id                BIGINT          NOT NULL,
    evento_catalogo_id          BIGINT          NOT NULL,
    origen                      VARCHAR(20)     NOT NULL,
    disparado_por_usuario_id    BIGINT,
    magnitud_aplicada           NUMERIC(6,4)    NOT NULL,
    duracion_aplicada           SMALLINT        NOT NULL,
    justificacion               TEXT,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_evento_competencia             PRIMARY KEY (id),
    CONSTRAINT fk_evcomp_competencia             FOREIGN KEY (competencia_id)
                                                 REFERENCES sim.competencia (id)
                                                 ON DELETE RESTRICT,
    CONSTRAINT fk_evcomp_trimestre               FOREIGN KEY (trimestre_id)
                                                 REFERENCES sim.trimestre (id)
                                                 ON DELETE RESTRICT,
    CONSTRAINT fk_evcomp_catalogo                FOREIGN KEY (evento_catalogo_id)
                                                 REFERENCES sim.evento_catalogo (id)
                                                 ON DELETE RESTRICT,
    CONSTRAINT fk_evcomp_usuario                 FOREIGN KEY (disparado_por_usuario_id)
                                                 REFERENCES sim.usuario (id)
                                                 ON DELETE RESTRICT,

    CONSTRAINT chk_evcomp_origen         CHECK (origen IN ('MODERADOR', 'SISTEMA', 'AUTOMATICO')),
    CONSTRAINT chk_evcomp_duracion       CHECK (duracion_aplicada >= 1)
);

CREATE INDEX idx_evcomp_competencia ON sim.evento_competencia (competencia_id);
CREATE INDEX idx_evcomp_trimestre   ON sim.evento_competencia (trimestre_id);

COMMENT ON TABLE sim.evento_competencia IS 'Instancia de un evento del catalogo aplicado en una competencia/trimestre';

-- DOWN
-- DROP TABLE IF EXISTS sim.evento_competencia;
