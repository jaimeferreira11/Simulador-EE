-- V202605121200__equipo_bot.sql
-- Soporte para equipos jugados por el sistema (bots)

ALTER TABLE sim.equipo
  ADD COLUMN tipo            VARCHAR(10) NOT NULL DEFAULT 'HUMANO',
  ADD COLUMN dificultad      VARCHAR(10),
  ADD COLUMN personalidad    VARCHAR(20),
  ADD COLUMN bot_config      JSONB;

ALTER TABLE sim.equipo
  ADD CONSTRAINT chk_equipo_tipo
    CHECK (tipo IN ('HUMANO', 'BOT')),
  ADD CONSTRAINT chk_equipo_dificultad
    CHECK (dificultad IS NULL OR dificultad IN ('FACIL','MEDIO','DIFICIL')),
  ADD CONSTRAINT chk_equipo_personalidad
    CHECK (personalidad IS NULL OR personalidad IN ('COST_LEADER','PREMIUM','BALANCEADO')),
  ADD CONSTRAINT chk_equipo_bot_completo
    CHECK (
      (tipo = 'BOT'    AND dificultad IS NOT NULL AND personalidad IS NOT NULL) OR
      (tipo = 'HUMANO' AND dificultad IS NULL     AND personalidad IS NULL)
    );

CREATE INDEX idx_equipo_tipo ON sim.equipo (competencia_id, tipo);

COMMENT ON COLUMN sim.equipo.tipo IS 'HUMANO: jugado por usuarios. BOT: jugado por el sistema.';
COMMENT ON COLUMN sim.equipo.dificultad IS 'Solo aplica si tipo=BOT. Define agresividad de la heurística.';
COMMENT ON COLUMN sim.equipo.personalidad IS 'Solo aplica si tipo=BOT. Define el estilo de juego.';
COMMENT ON COLUMN sim.equipo.bot_config IS 'JSON opcional para overrides finos por bot. Reservado para Fase 2.';

-- DOWN
-- ALTER TABLE sim.equipo
--   DROP CONSTRAINT chk_equipo_bot_completo,
--   DROP CONSTRAINT chk_equipo_personalidad,
--   DROP CONSTRAINT chk_equipo_dificultad,
--   DROP CONSTRAINT chk_equipo_tipo,
--   DROP COLUMN bot_config,
--   DROP COLUMN personalidad,
--   DROP COLUMN dificultad,
--   DROP COLUMN tipo;
-- DROP INDEX IF EXISTS sim.idx_equipo_tipo;
