-- =============================================================================
-- V202605131000__seed_dev_bots.sql
-- SOLO PARA DESARROLLO — Una competencia demo con bots visibles
--
-- Crea "Demo con Bots 2026" en estado ABIERTA_INSCRIPCION para que:
--   * El badge "BOT" sea visible en la lista del moderador sin tener que
--     crear competencias manualmente.
--   * Sirva de smoke-test rápido del soporte de equipos automatizados.
--
-- Idempotente: usa ON CONFLICT por código de competencia y por (competencia, nombre_empresa).
-- =============================================================================

SET search_path TO sim, public;

-- 1. Competencia Demo (ABIERTA_INSCRIPCION para que se pueda iniciar a mano)
INSERT INTO sim.competencia (
    codigo, nombre, rubro_id, parametro_macro_id, parametro_rubro_id, moderador_id,
    num_trimestres, num_equipos_max,
    caja_inicial, capacidad_inicial, headcount_inicial, salario_inicial,
    inventario_inicial, valor_planta_inicial,
    estado, entidad_id
)
SELECT
    'DEMO-BOTS-2026',
    'Demo con Bots 2026',
    (SELECT id FROM sim.rubro WHERE codigo = 'RETAIL_CONV'),
    (SELECT id FROM sim.parametro_macro WHERE nombre_set = 'PY_2026_BASE'),
    (SELECT id FROM sim.parametro_rubro WHERE codigo = 'RETAIL_CONV_BASE_2026'),
    (SELECT id FROM sim.usuario WHERE email = 'moderador@simulador.py'),
    4,
    8,
    500000000,
    50000,
    100,
    3500000,
    0,
    2500000000,
    'ABIERTA_INSCRIPCION',
    (SELECT id FROM sim.entidad ORDER BY id LIMIT 1)
ON CONFLICT (codigo) DO NOTHING;

-- 2. Equipos: 2 humanos (sin miembros, listos para invitar) + 2 bots
DO $$
DECLARE
    v_comp_id BIGINT := (SELECT id FROM sim.competencia WHERE codigo = 'DEMO-BOTS-2026');
BEGIN
    IF v_comp_id IS NULL THEN
        RAISE NOTICE 'Competencia DEMO-BOTS-2026 no encontrada, se omite seed de equipos.';
        RETURN;
    END IF;

    -- Humano 1 (placeholder, sin miembros)
    INSERT INTO sim.equipo (competencia_id, nombre_empresa, codigo_color, tipo, estado)
    SELECT v_comp_id, 'Demo Humanos A', '#1976D2', 'HUMANO', 'ACTIVO'
    WHERE NOT EXISTS (
        SELECT 1 FROM sim.equipo
        WHERE competencia_id = v_comp_id AND nombre_empresa = 'Demo Humanos A'
    );

    -- Humano 2
    INSERT INTO sim.equipo (competencia_id, nombre_empresa, codigo_color, tipo, estado)
    SELECT v_comp_id, 'Demo Humanos B', '#388E3C', 'HUMANO', 'ACTIVO'
    WHERE NOT EXISTS (
        SELECT 1 FROM sim.equipo
        WHERE competencia_id = v_comp_id AND nombre_empresa = 'Demo Humanos B'
    );

    -- Bot 1 — agresivo, líder en costos
    INSERT INTO sim.equipo (competencia_id, nombre_empresa, codigo_color, tipo, dificultad, personalidad, estado)
    SELECT v_comp_id, 'Bot Demo Aggressive', '#7C3AED', 'BOT', 'DIFICIL', 'COST_LEADER', 'ACTIVO'
    WHERE NOT EXISTS (
        SELECT 1 FROM sim.equipo
        WHERE competencia_id = v_comp_id AND nombre_empresa = 'Bot Demo Aggressive'
    );

    -- Bot 2 — premium, dificultad media
    INSERT INTO sim.equipo (competencia_id, nombre_empresa, codigo_color, tipo, dificultad, personalidad, estado)
    SELECT v_comp_id, 'Bot Demo Premium', '#EC4899', 'BOT', 'MEDIO', 'PREMIUM', 'ACTIVO'
    WHERE NOT EXISTS (
        SELECT 1 FROM sim.equipo
        WHERE competencia_id = v_comp_id AND nombre_empresa = 'Bot Demo Premium'
    );
END $$;

-- DOWN
-- DELETE FROM sim.equipo WHERE competencia_id = (SELECT id FROM sim.competencia WHERE codigo = 'DEMO-BOTS-2026');
-- DELETE FROM sim.competencia WHERE codigo = 'DEMO-BOTS-2026';
