-- V202605121300__bot_decision_log.sql
-- Fase 2 — soporte EXPERTO (LLM):
--   1) extiende el CHECK de equipo.dificultad para aceptar 'EXPERTO'
--   2) crea sim.bot_decision_log para auditar tokens / latencia / fallback
--      (solo se escribe para bots EXPERTO; los heurísticos no producen filas).

-- 1) Permitir EXPERTO en sim.equipo.dificultad
ALTER TABLE sim.equipo
  DROP CONSTRAINT chk_equipo_dificultad,
  ADD  CONSTRAINT chk_equipo_dificultad
    CHECK (dificultad IS NULL OR dificultad IN ('FACIL','MEDIO','DIFICIL','EXPERTO'));

COMMENT ON COLUMN sim.equipo.dificultad IS
  'Solo aplica si tipo=BOT. FACIL/MEDIO/DIFICIL: heurístico determinístico. EXPERTO: LLM con fallback a heurístico DIFICIL.';

-- 2) Tabla de auditoría


CREATE TABLE sim.bot_decision_log (
    id                BIGSERIAL    NOT NULL,
    equipo_id         BIGINT       NOT NULL,
    trimestre_id      BIGINT       NOT NULL,
    strategy_used     VARCHAR(20)  NOT NULL,
    latency_ms        INTEGER      NOT NULL,
    prompt_tokens     INTEGER,
    completion_tokens INTEGER,
    fallback_reason   VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_bot_decision_log PRIMARY KEY (id),
    CONSTRAINT chk_strategy_used   CHECK (strategy_used IN ('HEURISTIC','LLM','LLM_FALLBACK')),
    CONSTRAINT fk_bdl_equipo       FOREIGN KEY (equipo_id) REFERENCES sim.equipo(id) ON DELETE CASCADE,
    CONSTRAINT fk_bdl_trimestre    FOREIGN KEY (trimestre_id) REFERENCES sim.trimestre(id) ON DELETE CASCADE
);

CREATE INDEX idx_bdl_equipo_tri ON sim.bot_decision_log (equipo_id, trimestre_id);

COMMENT ON TABLE sim.bot_decision_log IS 'Auditoría de generación de decisiones bot (tokens, latencia, fallbacks). Solo se escribe para EXPERTO en Fase 2.';
COMMENT ON COLUMN sim.bot_decision_log.strategy_used IS 'HEURISTIC | LLM | LLM_FALLBACK. En Fase 2 se registran solo LLM y LLM_FALLBACK (los heurísticos no generan filas).';
COMMENT ON COLUMN sim.bot_decision_log.latency_ms IS 'Tiempo wall-clock de strategy.generate(ctx) en milisegundos.';
COMMENT ON COLUMN sim.bot_decision_log.prompt_tokens IS 'Tokens del prompt enviado al modelo (nullable si el provider no informa, ej. template).';
COMMENT ON COLUMN sim.bot_decision_log.completion_tokens IS 'Tokens de la respuesta del modelo (nullable si el provider no informa).';
COMMENT ON COLUMN sim.bot_decision_log.fallback_reason IS 'Motivo del fallback al heurístico (timeout LLM, JSON inválido, etc). NULL cuando strategy_used=LLM.';

-- DOWN
-- DROP INDEX IF EXISTS sim.idx_bdl_equipo_tri;
-- DROP TABLE IF EXISTS sim.bot_decision_log;
-- ALTER TABLE sim.equipo
--   DROP CONSTRAINT chk_equipo_dificultad,
--   ADD  CONSTRAINT chk_equipo_dificultad
--     CHECK (dificultad IS NULL OR dificultad IN ('FACIL','MEDIO','DIFICIL'));
