-- =============================================================================
-- V202604211013__seed_competencia_finalizada.sql
-- SOLO PARA DESARROLLO — Competencia finalizada "Simulación Primer Semestre 2025"
-- 4 trimestres completos, 3 equipos, 4 jugadores c/u, ganador definido
-- =============================================================================

SET search_path TO sim, public;

-- =============================================================================
-- 1. JUGADORES (12 jugadores para la competencia finalizada)
-- =============================================================================
DO $$
DECLARE
    v_hash TEXT := '$2a$06$fIUZIKdJwfwtGFdh4X6Xg.BofMiHuhtKZRuTteldZsx016ITfbDB6';
    v_rol_jugador BIGINT := (SELECT id FROM sim.rol_usuario WHERE codigo = 'JUGADOR');
BEGIN
    INSERT INTO sim.usuario (email, password_hash, nombre_completo, rol_usuario_id, activo, email_verificado) VALUES
    -- Equipo A: Itaipú Solutions
    ('capa1@simulador.py',  v_hash, 'Marcos Ayala',      v_rol_jugador, TRUE, TRUE),
    ('fina1@simulador.py',  v_hash, 'Lorena Cabañas',    v_rol_jugador, TRUE, TRUE),
    ('opsa1@simulador.py',  v_hash, 'Ricardo Bogado',    v_rol_jugador, TRUE, TRUE),
    ('coma1@simulador.py',  v_hash, 'Patricia Sosa',     v_rol_jugador, TRUE, TRUE),
    -- Equipo B: Chaco Industrial
    ('capb1@simulador.py',  v_hash, 'Gustavo Rojas',     v_rol_jugador, TRUE, TRUE),
    ('finb1@simulador.py',  v_hash, 'Silvia Mendoza',    v_rol_jugador, TRUE, TRUE),
    ('opsb1@simulador.py',  v_hash, 'Óscar Benítez',     v_rol_jugador, TRUE, TRUE),
    ('comb1@simulador.py',  v_hash, 'Claudia Ferreira',  v_rol_jugador, TRUE, TRUE),
    -- Equipo C: Asunción Trade
    ('capc1@simulador.py',  v_hash, 'Emilio Cantero',    v_rol_jugador, TRUE, TRUE),
    ('finc1@simulador.py',  v_hash, 'Andrea Villalba',   v_rol_jugador, TRUE, TRUE),
    ('opsc1@simulador.py',  v_hash, 'Tomás Espínola',    v_rol_jugador, TRUE, TRUE),
    ('comc1@simulador.py',  v_hash, 'Marta Duarte',      v_rol_jugador, TRUE, TRUE);
END $$;

-- =============================================================================
-- 2. COMPETENCIA FINALIZADA
-- =============================================================================
INSERT INTO sim.competencia (
    codigo, nombre, rubro_id, parametro_macro_id, parametro_rubro_id, moderador_id,
    num_trimestres, num_equipos_max,
    caja_inicial, capacidad_inicial, headcount_inicial, salario_inicial,
    inventario_inicial, valor_planta_inicial,
    estado, inicio_at, cierre_at
) VALUES (
    'RTL-2025A',
    'Simulación Primer Semestre 2025',
    (SELECT id FROM sim.rubro WHERE codigo = 'RETAIL_CONV'),
    (SELECT id FROM sim.parametro_macro WHERE nombre_set = 'PY_2026_BASE'),
    (SELECT id FROM sim.parametro_rubro WHERE codigo = 'RETAIL_CONV_BASE_2026'),
    (SELECT id FROM sim.usuario WHERE email = 'moderador@simulador.py'),
    4,   -- num_trimestres
    6,   -- num_equipos_max
    500000000,   -- caja_inicial
    50000,       -- capacidad_inicial
    100,         -- headcount_inicial
    3500000,     -- salario_inicial
    0,           -- inventario_inicial
    2500000000,  -- valor_planta
    'FINALIZADA',
    NOW() - INTERVAL '14 months',
    NOW() - INTERVAL '8 months'
);

-- =============================================================================
-- 3. EQUIPOS (3 equipos)
-- =============================================================================
DO $$
DECLARE
    v_comp BIGINT := (SELECT id FROM sim.competencia WHERE codigo = 'RTL-2025A');
BEGIN
    INSERT INTO sim.equipo (competencia_id, nombre_empresa, codigo_color, estado, posicion_final, pip_final) VALUES
    (v_comp, 'Itaipú Solutions',  '#0057A4', 'ACTIVO', 1, 68.40),
    (v_comp, 'Chaco Industrial',  '#8B0000', 'ACTIVO', 2, 55.20),
    (v_comp, 'Asunción Trade',    '#2E8B57', 'ACTIVO', 3, 42.80);
END $$;

-- =============================================================================
-- 4. MIEMBROS (4 por equipo)
-- =============================================================================
DO $$
DECLARE
    v_eqA BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Itaipú Solutions');
    v_eqB BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Chaco Industrial');
    v_eqC BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Asunción Trade');
    v_fin BIGINT := (SELECT id FROM sim.area_decision WHERE codigo = 'FINANZAS');
    v_ops BIGINT := (SELECT id FROM sim.area_decision WHERE codigo = 'OPERACIONES');
    v_com BIGINT := (SELECT id FROM sim.area_decision WHERE codigo = 'COMERCIAL');
BEGIN
    INSERT INTO sim.equipo_miembro (equipo_id, usuario_id, es_capitan, area_id) VALUES
    (v_eqA, (SELECT id FROM sim.usuario WHERE email='capa1@simulador.py'), TRUE,  NULL),
    (v_eqA, (SELECT id FROM sim.usuario WHERE email='fina1@simulador.py'), FALSE, v_fin),
    (v_eqA, (SELECT id FROM sim.usuario WHERE email='opsa1@simulador.py'), FALSE, v_ops),
    (v_eqA, (SELECT id FROM sim.usuario WHERE email='coma1@simulador.py'), FALSE, v_com),

    (v_eqB, (SELECT id FROM sim.usuario WHERE email='capb1@simulador.py'), TRUE,  NULL),
    (v_eqB, (SELECT id FROM sim.usuario WHERE email='finb1@simulador.py'), FALSE, v_fin),
    (v_eqB, (SELECT id FROM sim.usuario WHERE email='opsb1@simulador.py'), FALSE, v_ops),
    (v_eqB, (SELECT id FROM sim.usuario WHERE email='comb1@simulador.py'), FALSE, v_com),

    (v_eqC, (SELECT id FROM sim.usuario WHERE email='capc1@simulador.py'), TRUE,  NULL),
    (v_eqC, (SELECT id FROM sim.usuario WHERE email='finc1@simulador.py'), FALSE, v_fin),
    (v_eqC, (SELECT id FROM sim.usuario WHERE email='opsc1@simulador.py'), FALSE, v_ops),
    (v_eqC, (SELECT id FROM sim.usuario WHERE email='comc1@simulador.py'), FALSE, v_com);
END $$;

-- =============================================================================
-- 5. TRIMESTRES (Q0-Q4, todos PROCESADO)
-- =============================================================================
DO $$
DECLARE
    v_comp BIGINT := (SELECT id FROM sim.competencia WHERE codigo = 'RTL-2025A');
    v_base TIMESTAMPTZ := NOW() - INTERVAL '14 months';
BEGIN
    INSERT INTO sim.trimestre (competencia_id, numero, estado, apertura_at, cierre_at, procesado_at) VALUES
    (v_comp, 0, 'PROCESADO', v_base,                        v_base,                        v_base),
    (v_comp, 1, 'PROCESADO', v_base + INTERVAL '1 month',   v_base + INTERVAL '2 months',  v_base + INTERVAL '2 months'),
    (v_comp, 2, 'PROCESADO', v_base + INTERVAL '2 months',  v_base + INTERVAL '3 months',  v_base + INTERVAL '3 months'),
    (v_comp, 3, 'PROCESADO', v_base + INTERVAL '3 months',  v_base + INTERVAL '4 months',  v_base + INTERVAL '4 months'),
    (v_comp, 4, 'PROCESADO', v_base + INTERVAL '4 months',  v_base + INTERVAL '5 months',  v_base + INTERVAL '5 months');
END $$;

-- =============================================================================
-- 6. DECISIONES Q1-Q4 (3 equipos x 4 trimestres = 12 decisiones)
-- =============================================================================
DO $$
DECLARE
    v_eqA BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Itaipú Solutions');
    v_eqB BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Chaco Industrial');
    v_eqC BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Asunción Trade');
    v_base TIMESTAMPTZ := NOW() - INTERVAL '14 months';
    v_q BIGINT;
BEGIN
    -- ── Q1 ──
    v_q := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2025A' AND t.numero = 1);

    INSERT INTO sim.decision_equipo (equipo_id, trimestre_id, registrado_por_usuario_id,
        prestamo_solicitado, dividendos_pagar, produccion_planificada, compra_mp, inversion_capacidad,
        precio_venta, inversion_marketing, contrataciones_netas, aumento_salarial_pct, inversion_capacitacion,
        inversion_id, estado, submitted_at) VALUES
    (v_eqA, v_q, (SELECT id FROM sim.usuario WHERE email='capa1@simulador.py'),
     0, 0, 45000, 45000, 0, 25000, 25000000, 5, 0.0200, 8000000, 15000000,
     'PROCESADA', v_base + INTERVAL '1 month 20 days'),
    (v_eqB, v_q, (SELECT id FROM sim.usuario WHERE email='capb1@simulador.py'),
     0, 0, 48000, 48000, 0, 22000, 20000000, 0, 0.0100, 5000000, 5000000,
     'PROCESADA', v_base + INTERVAL '1 month 22 days'),
    (v_eqC, v_q, (SELECT id FROM sim.usuario WHERE email='capc1@simulador.py'),
     50000000, 0, 40000, 40000, 0, 28000, 15000000, 0, 0.0000, 3000000, 30000000,
     'PROCESADA', v_base + INTERVAL '1 month 25 days');

    -- ── Q2 ──
    v_q := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2025A' AND t.numero = 2);

    INSERT INTO sim.decision_equipo (equipo_id, trimestre_id, registrado_por_usuario_id,
        prestamo_solicitado, dividendos_pagar, produccion_planificada, compra_mp, inversion_capacidad,
        precio_venta, inversion_marketing, contrataciones_netas, aumento_salarial_pct, inversion_capacitacion,
        inversion_id, estado, submitted_at) VALUES
    (v_eqA, v_q, (SELECT id FROM sim.usuario WHERE email='capa1@simulador.py'),
     0, 0, 47000, 47000, 0, 26000, 30000000, 3, 0.0150, 10000000, 20000000,
     'PROCESADA', v_base + INTERVAL '2 month 18 days'),
    (v_eqB, v_q, (SELECT id FROM sim.usuario WHERE email='capb1@simulador.py'),
     0, 0, 50000, 50000, 50000000, 21000, 25000000, 5, 0.0100, 5000000, 5000000,
     'PROCESADA', v_base + INTERVAL '2 month 20 days'),
    (v_eqC, v_q, (SELECT id FROM sim.usuario WHERE email='capc1@simulador.py'),
     0, 0, 42000, 42000, 0, 27000, 20000000, 0, 0.0200, 5000000, 25000000,
     'PROCESADA', v_base + INTERVAL '2 month 22 days');

    -- ── Q3 ──
    v_q := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2025A' AND t.numero = 3);

    INSERT INTO sim.decision_equipo (equipo_id, trimestre_id, registrado_por_usuario_id,
        prestamo_solicitado, dividendos_pagar, produccion_planificada, compra_mp, inversion_capacidad,
        precio_venta, inversion_marketing, contrataciones_netas, aumento_salarial_pct, inversion_capacitacion,
        inversion_id, estado, submitted_at) VALUES
    (v_eqA, v_q, (SELECT id FROM sim.usuario WHERE email='capa1@simulador.py'),
     0, 50000000, 48000, 48000, 0, 27000, 35000000, 2, 0.0200, 12000000, 25000000,
     'PROCESADA', v_base + INTERVAL '3 month 15 days'),
    (v_eqB, v_q, (SELECT id FROM sim.usuario WHERE email='capb1@simulador.py'),
     100000000, 0, 52000, 52000, 0, 20000, 30000000, 5, 0.0150, 8000000, 10000000,
     'PROCESADA', v_base + INTERVAL '3 month 18 days'),
    (v_eqC, v_q, (SELECT id FROM sim.usuario WHERE email='capc1@simulador.py'),
     0, 0, 44000, 44000, 0, 26000, 25000000, 3, 0.0100, 8000000, 20000000,
     'PROCESADA', v_base + INTERVAL '3 month 20 days');

    -- ── Q4 ──
    v_q := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2025A' AND t.numero = 4);

    INSERT INTO sim.decision_equipo (equipo_id, trimestre_id, registrado_por_usuario_id,
        prestamo_solicitado, dividendos_pagar, produccion_planificada, compra_mp, inversion_capacidad,
        precio_venta, inversion_marketing, contrataciones_netas, aumento_salarial_pct, inversion_capacitacion,
        inversion_id, estado, submitted_at) VALUES
    (v_eqA, v_q, (SELECT id FROM sim.usuario WHERE email='capa1@simulador.py'),
     0, 100000000, 49000, 49000, 0, 28000, 40000000, 0, 0.0200, 15000000, 30000000,
     'PROCESADA', v_base + INTERVAL '4 month 12 days'),
    (v_eqB, v_q, (SELECT id FROM sim.usuario WHERE email='capb1@simulador.py'),
     0, 0, 55000, 55000, 0, 21000, 28000000, 3, 0.0100, 6000000, 8000000,
     'PROCESADA', v_base + INTERVAL '4 month 15 days'),
    (v_eqC, v_q, (SELECT id FROM sim.usuario WHERE email='capc1@simulador.py'),
     0, 0, 45000, 45000, 0, 25000, 22000000, 0, 0.0100, 6000000, 15000000,
     'PROCESADA', v_base + INTERVAL '4 month 18 days');
END $$;

-- =============================================================================
-- 7. RESULTADOS Q1-Q4
-- Itaipú: estrategia equilibrada (gana), Chaco: volumen/bajo precio, Asunción: premium/I+D
-- =============================================================================
DO $$
DECLARE
    v_eqA BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Itaipú Solutions');
    v_eqB BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Chaco Industrial');
    v_eqC BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Asunción Trade');
    v_q BIGINT;
BEGIN
    -- ── Q1 resultados ──
    v_q := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2025A' AND t.numero = 1);

    INSERT INTO sim.resultado_calculo (equipo_id, trimestre_id,
        utilizacion_capacidad, factor_eficiencia, produccion_real,
        demanda_total_mercado, demanda_asignada, competitividad, share, ventas_unidades,
        ingresos, costo_mp_total, costo_laboral, costo_fijo, costo_marketing, costo_id,
        costo_capacitacion, costo_almacenamiento, depreciacion, intereses, costos_operativos_total,
        utilidad_operativa, utilidad_antes_impuestos, impuesto_ire, utilidad_neta, pip_trimestre) VALUES
    (v_eqA, v_q, 0.9000, 0.9800, 44100, 190000, 66500, 1.12, 0.35, 44100,
     1102500000, 360000000, 487068750, 150000000, 25000000, 15000000,
     8000000, 0, 125000000, 0, 1170068750,
     -67568750, -67568750, 0, -67568750, 55.00),
    (v_eqB, v_q, 0.9600, 0.9800, 47040, 190000, 62700, 1.05, 0.33, 47040,
     1034880000, 384000000, 455625000, 150000000, 20000000, 5000000,
     5000000, 0, 125000000, 0, 1144625000,
     -109745000, -109745000, 0, -109745000, 45.00),
    (v_eqC, v_q, 0.8000, 0.9800, 39200, 190000, 60800, 0.83, 0.32, 39200,
     1097600000, 320000000, 455625000, 150000000, 15000000, 30000000,
     3000000, 0, 125000000, 1812500, 1100437500,
     -2837500, -2837500, 0, -2837500, 48.00);

    -- ── Q2 resultados ──
    v_q := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2025A' AND t.numero = 2);

    INSERT INTO sim.resultado_calculo (equipo_id, trimestre_id,
        utilizacion_capacidad, factor_eficiencia, produccion_real,
        demanda_total_mercado, demanda_asignada, competitividad, share, ventas_unidades,
        ingresos, costo_mp_total, costo_laboral, costo_fijo, costo_marketing, costo_id,
        costo_capacitacion, costo_almacenamiento, depreciacion, intereses, costos_operativos_total,
        utilidad_operativa, utilidad_antes_impuestos, impuesto_ire, utilidad_neta, pip_trimestre) VALUES
    (v_eqA, v_q, 0.9400, 0.9800, 46060, 200000, 72000, 1.18, 0.36, 46060,
     1197560000, 376000000, 505372500, 150000000, 30000000, 20000000,
     10000000, 0, 118750000, 0, 1210122500,
     -12562500, -12562500, 0, -12562500, 60.00),
    (v_eqB, v_q, 1.0000, 0.9500, 47500, 200000, 64000, 1.02, 0.32, 47500,
     997500000, 400000000, 472937500, 150000000, 25000000, 5000000,
     5000000, 0, 118750000, 0, 1176687500,
     -179187500, -179187500, 0, -179187500, 42.00),
    (v_eqC, v_q, 0.8400, 0.9800, 41160, 200000, 64000, 0.95, 0.32, 41160,
     1111320000, 336000000, 464737500, 150000000, 20000000, 25000000,
     5000000, 0, 118750000, 1812500, 1121300000,
     -9980000, -9980000, 0, -9980000, 52.00);

    -- ── Q3 resultados ── (estacionalidad 1.10 = 220k demanda)
    v_q := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2025A' AND t.numero = 3);

    INSERT INTO sim.resultado_calculo (equipo_id, trimestre_id,
        utilizacion_capacidad, factor_eficiencia, produccion_real,
        demanda_total_mercado, demanda_asignada, competitividad, share, ventas_unidades,
        ingresos, costo_mp_total, costo_laboral, costo_fijo, costo_marketing, costo_id,
        costo_capacitacion, costo_almacenamiento, depreciacion, intereses, costos_operativos_total,
        utilidad_operativa, utilidad_antes_impuestos, impuesto_ire, utilidad_neta, pip_trimestre) VALUES
    (v_eqA, v_q, 0.9600, 0.9900, 47520, 220000, 81400, 1.22, 0.37, 47520,
     1283040000, 384000000, 518625000, 150000000, 35000000, 25000000,
     12000000, 0, 112500000, 0, 1237125000,
     45915000, 45915000, 4591500, 41323500, 72.00),
    (v_eqB, v_q, 1.0000, 0.9600, 49920, 220000, 70400, 1.05, 0.32, 49920,
     998400000, 416000000, 498750000, 150000000, 30000000, 10000000,
     8000000, 0, 112500000, 3625000, 1228875000,
     -230475000, -230475000, 0, -230475000, 38.00),
    (v_eqC, v_q, 0.8800, 0.9800, 43120, 220000, 68200, 0.98, 0.31, 43120,
     1121120000, 352000000, 480187500, 150000000, 25000000, 20000000,
     8000000, 0, 112500000, 1812500, 1149500000,
     -28380000, -28380000, 0, -28380000, 50.00);

    -- ── Q4 resultados ── (estacionalidad 1.15 = 230k demanda, final)
    v_q := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2025A' AND t.numero = 4);

    INSERT INTO sim.resultado_calculo (equipo_id, trimestre_id,
        utilizacion_capacidad, factor_eficiencia, produccion_real,
        demanda_total_mercado, demanda_asignada, competitividad, share, ventas_unidades,
        ingresos, costo_mp_total, costo_laboral, costo_fijo, costo_marketing, costo_id,
        costo_capacitacion, costo_almacenamiento, depreciacion, intereses, costos_operativos_total,
        utilidad_operativa, utilidad_antes_impuestos, impuesto_ire, utilidad_neta, pip_trimestre) VALUES
    (v_eqA, v_q, 0.9800, 0.9900, 48510, 230000, 87400, 1.28, 0.38, 48510,
     1358280000, 392000000, 532125000, 150000000, 40000000, 30000000,
     15000000, 0, 106250000, 0, 1265375000,
     92905000, 92905000, 9290500, 83614500, 86.60),
    (v_eqB, v_q, 1.0000, 0.9700, 53350, 230000, 71300, 1.00, 0.31, 53350,
     1120350000, 440000000, 514687500, 150000000, 28000000, 8000000,
     6000000, 0, 106250000, 3625000, 1256562500,
     -136212500, -136212500, 0, -136212500, 35.80),
    (v_eqC, v_q, 0.9000, 0.9800, 44100, 230000, 71300, 0.97, 0.31, 44100,
     1102500000, 360000000, 488437500, 150000000, 22000000, 15000000,
     6000000, 0, 106250000, 1812500, 1149500000,
     -47000000, -47000000, 0, -47000000, 45.20);
END $$;

-- =============================================================================
-- 8. SNAPSHOTS Q0 INICIO + Q1-Q4 INICIO/CIERRE
-- =============================================================================
DO $$
DECLARE
    v_eqA BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Itaipú Solutions');
    v_eqB BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Chaco Industrial');
    v_eqC BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Asunción Trade');
    v_q BIGINT;
BEGIN
    -- Q0 INICIO (todos iguales)
    v_q := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2025A' AND t.numero = 0);
    INSERT INTO sim.snapshot_estado (equipo_id, trimestre_id, momento, caja, deuda, patrimonio_neto, valor_planta, capacidad, headcount, salario, inventario, brand_equity, calidad_percibida, id_acumulado, pip) VALUES
    (v_eqA, v_q, 'INICIO', 500000000, 0, 3000000000, 2500000000, 50000, 100, 3500000, 0, 50.00, 50.00, 0, 50.00),
    (v_eqB, v_q, 'INICIO', 500000000, 0, 3000000000, 2500000000, 50000, 100, 3500000, 0, 50.00, 50.00, 0, 50.00),
    (v_eqC, v_q, 'INICIO', 500000000, 0, 3000000000, 2500000000, 50000, 100, 3500000, 0, 50.00, 50.00, 0, 50.00);

    -- Q1 INICIO = Q0 INICIO
    v_q := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2025A' AND t.numero = 1);
    INSERT INTO sim.snapshot_estado (equipo_id, trimestre_id, momento, caja, deuda, patrimonio_neto, valor_planta, capacidad, headcount, salario, inventario, brand_equity, calidad_percibida, id_acumulado, pip) VALUES
    (v_eqA, v_q, 'INICIO', 500000000, 0, 3000000000, 2500000000, 50000, 100, 3500000, 0, 50.00, 50.00, 0, 50.00),
    (v_eqB, v_q, 'INICIO', 500000000, 0, 3000000000, 2500000000, 50000, 100, 3500000, 0, 50.00, 50.00, 0, 50.00),
    (v_eqC, v_q, 'INICIO', 500000000, 0, 3000000000, 2500000000, 50000, 100, 3500000, 0, 50.00, 50.00, 0, 50.00);

    -- Q1 CIERRE
    INSERT INTO sim.snapshot_estado (equipo_id, trimestre_id, momento, caja, deuda, patrimonio_neto, valor_planta, capacidad, headcount, salario, inventario, brand_equity, calidad_percibida, id_acumulado, pip) VALUES
    (v_eqA, v_q, 'CIERRE', 432431250, 0, 2932431250, 2375000000, 50000, 105, 3570000, 0, 53.00, 52.50, 15000000, 55.00),
    (v_eqB, v_q, 'CIERRE', 390255000, 0, 2890255000, 2375000000, 50000, 100, 3535000, 0, 48.50, 50.50, 5000000, 45.00),
    (v_eqC, v_q, 'CIERRE', 447162500, 50000000, 2947162500, 2375000000, 50000, 100, 3500000, 0, 51.50, 54.00, 30000000, 48.00);

    -- Q2 INICIO = Q1 CIERRE
    v_q := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2025A' AND t.numero = 2);
    INSERT INTO sim.snapshot_estado (equipo_id, trimestre_id, momento, caja, deuda, patrimonio_neto, valor_planta, capacidad, headcount, salario, inventario, brand_equity, calidad_percibida, id_acumulado, pip) VALUES
    (v_eqA, v_q, 'INICIO', 432431250, 0, 2932431250, 2375000000, 50000, 105, 3570000, 0, 53.00, 52.50, 15000000, 55.00),
    (v_eqB, v_q, 'INICIO', 390255000, 0, 2890255000, 2375000000, 50000, 100, 3535000, 0, 48.50, 50.50, 5000000, 45.00),
    (v_eqC, v_q, 'INICIO', 447162500, 50000000, 2947162500, 2375000000, 50000, 100, 3500000, 0, 51.50, 54.00, 30000000, 48.00);

    -- Q2 CIERRE
    INSERT INTO sim.snapshot_estado (equipo_id, trimestre_id, momento, caja, deuda, patrimonio_neto, valor_planta, capacidad, headcount, salario, inventario, brand_equity, calidad_percibida, id_acumulado, pip) VALUES
    (v_eqA, v_q, 'CIERRE', 419868750, 0, 2919868750, 2256250000, 50000, 108, 3623550, 0, 55.50, 55.00, 35000000, 60.00),
    (v_eqB, v_q, 'CIERRE', 161067500, 0, 2661067500, 2306250000, 51000, 105, 3570350, 0, 49.00, 51.00, 10000000, 42.00),
    (v_eqC, v_q, 'CIERRE', 437182500, 50000000, 2937182500, 2256250000, 50000, 100, 3570000, 0, 53.00, 57.50, 55000000, 52.00);

    -- Q3 INICIO = Q2 CIERRE
    v_q := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2025A' AND t.numero = 3);
    INSERT INTO sim.snapshot_estado (equipo_id, trimestre_id, momento, caja, deuda, patrimonio_neto, valor_planta, capacidad, headcount, salario, inventario, brand_equity, calidad_percibida, id_acumulado, pip) VALUES
    (v_eqA, v_q, 'INICIO', 419868750, 0, 2919868750, 2256250000, 50000, 108, 3623550, 0, 55.50, 55.00, 35000000, 60.00),
    (v_eqB, v_q, 'INICIO', 161067500, 0, 2661067500, 2306250000, 51000, 105, 3570350, 0, 49.00, 51.00, 10000000, 42.00),
    (v_eqC, v_q, 'INICIO', 437182500, 50000000, 2937182500, 2256250000, 50000, 100, 3570000, 0, 53.00, 57.50, 55000000, 52.00);

    -- Q3 CIERRE
    INSERT INTO sim.snapshot_estado (equipo_id, trimestre_id, momento, caja, deuda, patrimonio_neto, valor_planta, capacidad, headcount, salario, inventario, brand_equity, calidad_percibida, id_acumulado, pip) VALUES
    (v_eqA, v_q, 'CIERRE', 411192250, 0, 2961192250, 2143437500, 50000, 110, 3696021, 0, 58.20, 58.00, 60000000, 72.00),
    (v_eqB, v_q, 'CIERRE', 30592500, 100000000, 2530592500, 2190937500, 51000, 110, 3623904, 0, 47.50, 52.00, 20000000, 38.00),
    (v_eqC, v_q, 'CIERRE', 408802500, 50000000, 2908802500, 2143437500, 50000, 103, 3605700, 0, 54.80, 60.50, 75000000, 50.00);

    -- Q4 INICIO = Q3 CIERRE
    v_q := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2025A' AND t.numero = 4);
    INSERT INTO sim.snapshot_estado (equipo_id, trimestre_id, momento, caja, deuda, patrimonio_neto, valor_planta, capacidad, headcount, salario, inventario, brand_equity, calidad_percibida, id_acumulado, pip) VALUES
    (v_eqA, v_q, 'INICIO', 411192250, 0, 2961192250, 2143437500, 50000, 110, 3696021, 0, 58.20, 58.00, 60000000, 72.00),
    (v_eqB, v_q, 'INICIO', 30592500, 100000000, 2530592500, 2190937500, 51000, 110, 3623904, 0, 47.50, 52.00, 20000000, 38.00),
    (v_eqC, v_q, 'INICIO', 408802500, 50000000, 2908802500, 2143437500, 50000, 103, 3605700, 0, 54.80, 60.50, 75000000, 50.00);

    -- Q4 CIERRE (final)
    INSERT INTO sim.snapshot_estado (equipo_id, trimestre_id, momento, caja, deuda, patrimonio_neto, valor_planta, capacidad, headcount, salario, inventario, brand_equity, calidad_percibida, id_acumulado, pip) VALUES
    (v_eqA, v_q, 'CIERRE', 394806750, 0, 3044806750, 2036265625, 50000, 110, 3769941, 0, 61.00, 61.00, 90000000, 86.60),
    (v_eqB, v_q, 'CIERRE', -5620000, 100000000, 2394380000, 2081390625, 51000, 113, 3660143, 0, 46.00, 53.00, 28000000, 35.80),
    (v_eqC, v_q, 'CIERRE', 361802500, 50000000, 2861802500, 2036265625, 50000, 103, 3641757, 0, 55.50, 63.00, 90000000, 45.20);
END $$;

-- =============================================================================
-- 9. RANKINGS Q1-Q4
-- =============================================================================
DO $$
DECLARE
    v_comp BIGINT := (SELECT id FROM sim.competencia WHERE codigo = 'RTL-2025A');
    v_eqA BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Itaipú Solutions');
    v_eqB BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Chaco Industrial');
    v_eqC BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Asunción Trade');
    v_q BIGINT;
BEGIN
    -- Q1
    v_q := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2025A' AND t.numero = 1);
    INSERT INTO sim.ranking_trimestre (competencia_id, trimestre_id, equipo_id, posicion, pip_acumulado, utilidad_acumulada, caja_actual, share_actual) VALUES
    (v_comp, v_q, v_eqA, 1, 55.00, -67568750,  432431250, 0.35),
    (v_comp, v_q, v_eqC, 2, 48.00, -2837500,   447162500, 0.32),
    (v_comp, v_q, v_eqB, 3, 45.00, -109745000, 390255000, 0.33);

    -- Q2
    v_q := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2025A' AND t.numero = 2);
    INSERT INTO sim.ranking_trimestre (competencia_id, trimestre_id, equipo_id, posicion, pip_acumulado, utilidad_acumulada, caja_actual, share_actual) VALUES
    (v_comp, v_q, v_eqA, 1, 57.50, -80131250,  419868750, 0.36),
    (v_comp, v_q, v_eqC, 2, 50.00, -12817500,  437182500, 0.32),
    (v_comp, v_q, v_eqB, 3, 43.50, -288932500, 161067500, 0.32);

    -- Q3
    v_q := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2025A' AND t.numero = 3);
    INSERT INTO sim.ranking_trimestre (competencia_id, trimestre_id, equipo_id, posicion, pip_acumulado, utilidad_acumulada, caja_actual, share_actual) VALUES
    (v_comp, v_q, v_eqA, 1, 62.33, -38807750,  411192250, 0.37),
    (v_comp, v_q, v_eqC, 2, 50.00, -41197500,  408802500, 0.31),
    (v_comp, v_q, v_eqB, 3, 41.67, -519407500, 30592500,  0.32);

    -- Q4 (final)
    v_q := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2025A' AND t.numero = 4);
    INSERT INTO sim.ranking_trimestre (competencia_id, trimestre_id, equipo_id, posicion, pip_acumulado, utilidad_acumulada, caja_actual, share_actual) VALUES
    (v_comp, v_q, v_eqA, 1, 68.40, 44806750,   394806750, 0.38),
    (v_comp, v_q, v_eqC, 2, 48.80, -88197500,  361802500, 0.31),
    (v_comp, v_q, v_eqB, 3, 40.20, -655620000, -5620000,  0.31);
END $$;

-- =============================================================================
-- 10. AUDITORÍA EVENTOS
-- =============================================================================
DO $$
DECLARE
    v_comp BIGINT := (SELECT id FROM sim.competencia WHERE codigo = 'RTL-2025A');
    v_mod  BIGINT := (SELECT id FROM sim.usuario WHERE email = 'moderador@simulador.py');
    v_base TIMESTAMPTZ := NOW() - INTERVAL '14 months';
BEGIN
    INSERT INTO sim.auditoria_evento (competencia_id, usuario_id, tipo_accion, descripcion, ocurrido_at) VALUES
    (v_comp, v_mod, 'COMPETENCIA_CREADA',  'Se creó la competencia Simulación Primer Semestre 2025',  v_base),
    (v_comp, v_mod, 'INSCRIPCION_ABIERTA', 'Se abrió la inscripción para equipos',                   v_base + INTERVAL '7 days'),
    (v_comp, v_mod, 'COMPETENCIA_INICIADA', 'Se inició la competencia con 3 equipos inscritos',       v_base + INTERVAL '1 month'),
    (v_comp, v_mod, 'TRIMESTRE_ABIERTO',    'Se abrió Q1 para recibir decisiones',                   v_base + INTERVAL '1 month'),
    (v_comp, v_mod, 'TRIMESTRE_CERRADO',    'Se cerró Q1 y se procesaron resultados',                v_base + INTERVAL '2 months'),
    (v_comp, v_mod, 'TRIMESTRE_ABIERTO',    'Se abrió Q2 para recibir decisiones',                   v_base + INTERVAL '2 months'),
    (v_comp, v_mod, 'TRIMESTRE_CERRADO',    'Se cerró Q2 y se procesaron resultados',                v_base + INTERVAL '3 months'),
    (v_comp, v_mod, 'TRIMESTRE_ABIERTO',    'Se abrió Q3 para recibir decisiones',                   v_base + INTERVAL '3 months'),
    (v_comp, v_mod, 'TRIMESTRE_CERRADO',    'Se cerró Q3 y se procesaron resultados',                v_base + INTERVAL '4 months'),
    (v_comp, v_mod, 'TRIMESTRE_ABIERTO',    'Se abrió Q4 para recibir decisiones',                   v_base + INTERVAL '4 months'),
    (v_comp, v_mod, 'TRIMESTRE_CERRADO',    'Se cerró Q4 y se procesaron resultados. Competencia finalizada.', v_base + INTERVAL '5 months');
END $$;

-- =============================================================================
-- 11. AUDITORÍA DECISIONES (CREADA + ENVIADA para cada una de las 12 decisiones)
-- =============================================================================
DO $$
DECLARE
    v_dec RECORD;
    v_estado_borrador JSONB;
    v_estado_enviada JSONB;
BEGIN
    FOR v_dec IN
        SELECT de.id, de.registrado_por_usuario_id, de.submitted_at,
               de.prestamo_solicitado, de.dividendos_pagar,
               de.produccion_planificada, de.compra_mp, de.inversion_capacidad,
               de.precio_venta, de.inversion_marketing,
               de.contrataciones_netas, de.aumento_salarial_pct, de.inversion_capacitacion,
               de.inversion_id
        FROM sim.decision_equipo de
        JOIN sim.trimestre t ON de.trimestre_id = t.id
        JOIN sim.competencia c ON t.competencia_id = c.id
        WHERE c.codigo = 'RTL-2025A'
        ORDER BY t.numero, de.equipo_id
    LOOP
        v_estado_borrador := jsonb_build_object(
            'estado', 'BORRADOR',
            'precio_venta', v_dec.precio_venta,
            'inversion_marketing', v_dec.inversion_marketing,
            'produccion_planificada', v_dec.produccion_planificada,
            'compra_mp', v_dec.compra_mp,
            'inversion_capacidad', v_dec.inversion_capacidad,
            'inversion_id', v_dec.inversion_id,
            'prestamo_solicitado', v_dec.prestamo_solicitado,
            'dividendos_pagar', v_dec.dividendos_pagar,
            'contrataciones_netas', v_dec.contrataciones_netas,
            'aumento_salarial_pct', v_dec.aumento_salarial_pct,
            'inversion_capacitacion', v_dec.inversion_capacitacion
        );
        v_estado_enviada := v_estado_borrador || '{"estado": "ENVIADA"}'::jsonb;

        INSERT INTO sim.auditoria_decision (decision_equipo_id, usuario_id, accion, estado_anterior, estado_nuevo, ip_origen, ocurrido_at) VALUES
        (v_dec.id, v_dec.registrado_por_usuario_id, 'CREADA',  NULL, v_estado_borrador, '10.0.0.50'::INET, v_dec.submitted_at - INTERVAL '2 days'),
        (v_dec.id, v_dec.registrado_por_usuario_id, 'ENVIADA', v_estado_borrador, v_estado_enviada, '10.0.0.50'::INET, v_dec.submitted_at);
    END LOOP;
END $$;
