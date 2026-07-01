-- =============================================================================
-- V202605261200__seed_competencia_demo.sql
-- Crea (o reemplaza) la competencia DEMO usada para presentaciones.
--
-- Idempotente: ON CONFLICT por código / email / (competencia, nombre_empresa).
-- Reemplaza la competencia previa DEMO-BOTS-2026 si existe.
-- =============================================================================

SET search_path TO sim, public;

-- 1. Limpia la competencia previa DEMO-BOTS-2026 (CASCADE no aplica — borrado manual ordenado)
DO $$
DECLARE
    v_old_id BIGINT := (SELECT id FROM sim.competencia WHERE codigo = 'DEMO-BOTS-2026');
BEGIN
    IF v_old_id IS NOT NULL THEN
        DELETE FROM sim.equipo_miembro WHERE equipo_id IN (SELECT id FROM sim.equipo WHERE competencia_id = v_old_id);
        DELETE FROM sim.equipo WHERE competencia_id = v_old_id;
        DELETE FROM sim.trimestre WHERE competencia_id = v_old_id;
        DELETE FROM sim.competencia WHERE id = v_old_id;
    END IF;
END $$;

-- 2. Usuario CEO sintético (no puede autenticarse: password_hash inusable, activo=FALSE).
INSERT INTO sim.usuario (
    email, password_hash, nombre_completo, rol_usuario_id, activo, email_verificado
)
SELECT
    'ceo.demo@simulador.py',
    '!unusable!',
    'CEO Demo',
    r.id,
    FALSE,
    TRUE
FROM sim.rol_usuario r
WHERE r.codigo = 'JUGADOR'
ON CONFLICT (email) DO NOTHING;

-- 3. Competencia DEMO
INSERT INTO sim.competencia (
    codigo, nombre, rubro_id, parametro_macro_id, parametro_rubro_id, moderador_id,
    num_trimestres, num_equipos_max,
    caja_inicial, capacidad_inicial, headcount_inicial, salario_inicial,
    inventario_inicial, valor_planta_inicial,
    estado, entidad_id, ia_habilitada
)
SELECT
    'DEMO',
    'Demo del Simulador',
    (SELECT id FROM sim.rubro WHERE codigo = 'RETAIL_CONV'),
    (SELECT id FROM sim.parametro_macro WHERE nombre_set = 'PY_2026_BASE'),
    (SELECT id FROM sim.parametro_rubro WHERE codigo = 'RETAIL_CONV_BASE_2026'),
    (SELECT id FROM sim.usuario WHERE email = 'moderador@simulador.py'),
    4, 4,
    500000000, 50000, 100, 3500000, 0, 2500000000,
    'EN_CURSO',
    (SELECT id FROM sim.entidad ORDER BY id LIMIT 1),
    TRUE
ON CONFLICT (codigo) DO NOTHING;

-- 4. Equipos (1 humano + 3 bots) y miembro CEO
DO $$
DECLARE
    v_comp_id   BIGINT := (SELECT id FROM sim.competencia WHERE codigo = 'DEMO');
    v_ceo_id    BIGINT := (SELECT id FROM sim.usuario    WHERE email  = 'ceo.demo@simulador.py');
    v_equipo_id BIGINT;
BEGIN
    IF v_comp_id IS NULL THEN
        RAISE NOTICE 'Competencia DEMO no encontrada; se omite seed de equipos.';
        RETURN;
    END IF;

    -- Equipo CEO humano
    INSERT INTO sim.equipo (competencia_id, nombre_empresa, codigo_color, tipo, estado)
    SELECT v_comp_id, 'Equipo Demo CEO', '#1976D2', 'HUMANO', 'ACTIVO'
    WHERE NOT EXISTS (
        SELECT 1 FROM sim.equipo
        WHERE competencia_id = v_comp_id AND nombre_empresa = 'Equipo Demo CEO'
    );

    v_equipo_id := (SELECT id FROM sim.equipo
                    WHERE competencia_id = v_comp_id AND nombre_empresa = 'Equipo Demo CEO');

    -- Miembro CEO (es_capitan=TRUE porque es el único miembro)
    IF v_ceo_id IS NOT NULL AND v_equipo_id IS NOT NULL THEN
        INSERT INTO sim.equipo_miembro (equipo_id, usuario_id, es_capitan)
        SELECT v_equipo_id, v_ceo_id, TRUE
        WHERE NOT EXISTS (
            SELECT 1 FROM sim.equipo_miembro
            WHERE equipo_id = v_equipo_id AND usuario_id = v_ceo_id
        );
    END IF;

    -- Bot 1 — agresivo, líder en costos
    INSERT INTO sim.equipo (competencia_id, nombre_empresa, codigo_color, tipo, dificultad, personalidad, estado)
    SELECT v_comp_id, 'Bot Líder en Costos', '#7C3AED', 'BOT', 'DIFICIL', 'COST_LEADER', 'ACTIVO'
    WHERE NOT EXISTS (
        SELECT 1 FROM sim.equipo
        WHERE competencia_id = v_comp_id AND nombre_empresa = 'Bot Líder en Costos'
    );

    -- Bot 2 — premium
    INSERT INTO sim.equipo (competencia_id, nombre_empresa, codigo_color, tipo, dificultad, personalidad, estado)
    SELECT v_comp_id, 'Bot Premium', '#EC4899', 'BOT', 'MEDIO', 'PREMIUM', 'ACTIVO'
    WHERE NOT EXISTS (
        SELECT 1 FROM sim.equipo
        WHERE competencia_id = v_comp_id AND nombre_empresa = 'Bot Premium'
    );

    -- Bot 3 — balanceado
    INSERT INTO sim.equipo (competencia_id, nombre_empresa, codigo_color, tipo, dificultad, personalidad, estado)
    SELECT v_comp_id, 'Bot Balanceado', '#10B981', 'BOT', 'FACIL', 'BALANCEADO', 'ACTIVO'
    WHERE NOT EXISTS (
        SELECT 1 FROM sim.equipo
        WHERE competencia_id = v_comp_id AND nombre_empresa = 'Bot Balanceado'
    );
END $$;

-- 5. Trimestres 1..4 en PENDIENTE (el DemoBootstrapService abre Q1 al arrancar la app)
DO $$
DECLARE
    v_comp_id BIGINT := (SELECT id FROM sim.competencia WHERE codigo = 'DEMO');
    i INT;
BEGIN
    IF v_comp_id IS NULL THEN
        RETURN;
    END IF;

    FOR i IN 1..4 LOOP
        INSERT INTO sim.trimestre (competencia_id, numero, estado)
        SELECT v_comp_id, i, 'PENDIENTE'
        WHERE NOT EXISTS (
            SELECT 1 FROM sim.trimestre
            WHERE competencia_id = v_comp_id AND numero = i
        );
    END LOOP;
END $$;

-- DOWN (rollback manual):
-- DELETE FROM sim.equipo_miembro WHERE equipo_id IN (SELECT id FROM sim.equipo WHERE competencia_id = (SELECT id FROM sim.competencia WHERE codigo='DEMO'));
-- DELETE FROM sim.equipo      WHERE competencia_id = (SELECT id FROM sim.competencia WHERE codigo='DEMO');
-- DELETE FROM sim.trimestre   WHERE competencia_id = (SELECT id FROM sim.competencia WHERE codigo='DEMO');
-- DELETE FROM sim.competencia WHERE codigo='DEMO';
-- DELETE FROM sim.usuario     WHERE email='ceo.demo@simulador.py';
