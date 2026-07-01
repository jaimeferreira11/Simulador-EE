-- =============================================================================
-- V202604211012__seed_dev_data.sql
-- SOLO PARA DESARROLLO — Datos de prueba realistas
-- Competencia "Retail Championship 2026" en Q3 EN_CURSO con 4 equipos de 6 jugadores
-- Q1 y Q2 ya procesados con decisiones y resultados basados en el Golden File
-- =============================================================================

SET search_path TO sim, public;

-- =============================================================================
-- 1. JUGADORES (24 jugadores, 6 por equipo)
-- Todos usan password: password123
-- =============================================================================
DO $$
DECLARE
    v_hash TEXT := '$2a$06$fIUZIKdJwfwtGFdh4X6Xg.BofMiHuhtKZRuTteldZsx016ITfbDB6';
    v_rol_admin     BIGINT := (SELECT id FROM sim.rol_usuario WHERE codigo = 'ADMIN_PLATAFORMA');
    v_rol_moderador BIGINT := (SELECT id FROM sim.rol_usuario WHERE codigo = 'MODERADOR');
    v_rol_jugador   BIGINT := (SELECT id FROM sim.rol_usuario WHERE codigo = 'JUGADOR');
BEGIN
    INSERT INTO sim.usuario (email, password_hash, nombre_completo, rol_usuario_id, activo, email_verificado) VALUES
    -- Admin y Moderador
    ('admin@simulador.py',     v_hash, 'Admin Plataforma',  v_rol_admin,     TRUE, TRUE),
    ('moderador@simulador.py', v_hash, 'Prof. María González', v_rol_moderador, TRUE, TRUE),

    -- Equipo 1: Guaraní Market
    ('capitan1@simulador.py',  v_hash, 'Ana Benítez',      v_rol_jugador, TRUE, TRUE),
    ('finanzas1@simulador.py', v_hash, 'Carlos Giménez',   v_rol_jugador, TRUE, TRUE),
    ('operaciones1@simulador.py', v_hash, 'Diego Martínez', v_rol_jugador, TRUE, TRUE),
    ('comercial1@simulador.py', v_hash, 'Elena Romero',    v_rol_jugador, TRUE, TRUE),
    ('talento1@simulador.py',  v_hash, 'Fernando López',   v_rol_jugador, TRUE, TRUE),
    ('apoyo1@simulador.py',    v_hash, 'Gabriela Acosta',  v_rol_jugador, TRUE, TRUE),

    -- Equipo 2: Ñandutí Express
    ('capitan2@simulador.py',  v_hash, 'Hugo Villalba',    v_rol_jugador, TRUE, TRUE),
    ('finanzas2@simulador.py', v_hash, 'Isabel Duarte',    v_rol_jugador, TRUE, TRUE),
    ('operaciones2@simulador.py', v_hash, 'Jorge Cabrera', v_rol_jugador, TRUE, TRUE),
    ('comercial2@simulador.py', v_hash, 'Karen Fleitas',   v_rol_jugador, TRUE, TRUE),
    ('talento2@simulador.py',  v_hash, 'Luis Espínola',    v_rol_jugador, TRUE, TRUE),
    ('apoyo2@simulador.py',    v_hash, 'María Paredes',    v_rol_jugador, TRUE, TRUE),

    -- Equipo 3: Cerro Corá Trading
    ('capitan3@simulador.py',  v_hash, 'Nelson Agüero',    v_rol_jugador, TRUE, TRUE),
    ('finanzas3@simulador.py', v_hash, 'Olga Caballero',   v_rol_jugador, TRUE, TRUE),
    ('operaciones3@simulador.py', v_hash, 'Pablo Lezcano', v_rol_jugador, TRUE, TRUE),
    ('comercial3@simulador.py', v_hash, 'Raquel Bogado',   v_rol_jugador, TRUE, TRUE),
    ('talento3@simulador.py',  v_hash, 'Santiago Vera',    v_rol_jugador, TRUE, TRUE),
    ('apoyo3@simulador.py',    v_hash, 'Teresa Núñez',     v_rol_jugador, TRUE, TRUE),

    -- Equipo 4: Ypacaraí Retail
    ('capitan4@simulador.py',  v_hash, 'Ulises Franco',    v_rol_jugador, TRUE, TRUE),
    ('finanzas4@simulador.py', v_hash, 'Valeria Insaurralde', v_rol_jugador, TRUE, TRUE),
    ('operaciones4@simulador.py', v_hash, 'Walter Godoy', v_rol_jugador, TRUE, TRUE),
    ('comercial4@simulador.py', v_hash, 'Ximena Ramírez',  v_rol_jugador, TRUE, TRUE),
    ('talento4@simulador.py',  v_hash, 'Yamil Ortega',     v_rol_jugador, TRUE, TRUE),
    ('apoyo4@simulador.py',    v_hash, 'Zulma Cardozo',    v_rol_jugador, TRUE, TRUE);
END $$;

-- =============================================================================
-- 2. COMPETENCIA
-- =============================================================================
INSERT INTO sim.competencia (
    codigo, nombre, rubro_id, parametro_macro_id, parametro_rubro_id, moderador_id,
    num_trimestres, num_equipos_max,
    caja_inicial, capacidad_inicial, headcount_inicial, salario_inicial,
    inventario_inicial, valor_planta_inicial,
    estado, inicio_at
) VALUES (
    'RTL-2026A',
    'Retail Championship 2026',
    (SELECT id FROM sim.rubro WHERE codigo = 'RETAIL_CONV'),
    (SELECT id FROM sim.parametro_macro WHERE nombre_set = 'PY_2026_BASE'),
    (SELECT id FROM sim.parametro_rubro WHERE codigo = 'RETAIL_CONV_BASE_2026'),
    (SELECT id FROM sim.usuario WHERE email = 'moderador@simulador.py'),
    4,   -- num_trimestres
    8,   -- num_equipos_max
    500000000,   -- caja_inicial:      Gs. 500M
    50000,       -- capacidad_inicial: 50.000 uds/trim
    100,         -- headcount_inicial: 100 empleados
    3500000,     -- salario_inicial:   Gs. 3.5M/mes
    0,           -- inventario_inicial
    2500000000,  -- valor_planta:      Gs. 2.500M
    'EN_CURSO',
    NOW() - INTERVAL '6 months'
);

-- =============================================================================
-- 3. EQUIPOS (4 equipos)
-- =============================================================================
DO $$
DECLARE
    v_comp_id BIGINT := (SELECT id FROM sim.competencia WHERE codigo = 'RTL-2026A');
BEGIN
    INSERT INTO sim.equipo (competencia_id, nombre_empresa, codigo_color, estado) VALUES
    (v_comp_id, 'Guaraní Market',     '#006B3F', 'ACTIVO'),
    (v_comp_id, 'Ñandutí Express',    '#D4213D', 'ACTIVO'),
    (v_comp_id, 'Cerro Corá Trading', '#1E3A5F', 'ACTIVO'),
    (v_comp_id, 'Ypacaraí Retail',    '#F47920', 'ACTIVO');
END $$;

-- =============================================================================
-- 4. MIEMBROS CON ÁREAS (6 por equipo: capitán + 4 áreas + 1 apoyo)
-- =============================================================================
DO $$
DECLARE
    v_eq1 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Guaraní Market');
    v_eq2 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Ñandutí Express');
    v_eq3 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Cerro Corá Trading');
    v_eq4 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Ypacaraí Retail');
    v_fin BIGINT := (SELECT id FROM sim.area_decision WHERE codigo = 'FINANZAS');
    v_ops BIGINT := (SELECT id FROM sim.area_decision WHERE codigo = 'OPERACIONES');
    v_com BIGINT := (SELECT id FROM sim.area_decision WHERE codigo = 'COMERCIAL');
    v_th  BIGINT := (SELECT id FROM sim.area_decision WHERE codigo = 'TALENTO_HUMANO');
BEGIN
    -- Equipo 1: Guaraní Market
    INSERT INTO sim.equipo_miembro (equipo_id, usuario_id, es_capitan, area_id) VALUES
    (v_eq1, (SELECT id FROM sim.usuario WHERE email='capitan1@simulador.py'),     TRUE,  NULL),
    (v_eq1, (SELECT id FROM sim.usuario WHERE email='finanzas1@simulador.py'),    FALSE, v_fin),
    (v_eq1, (SELECT id FROM sim.usuario WHERE email='operaciones1@simulador.py'), FALSE, v_ops),
    (v_eq1, (SELECT id FROM sim.usuario WHERE email='comercial1@simulador.py'),   FALSE, v_com),
    (v_eq1, (SELECT id FROM sim.usuario WHERE email='talento1@simulador.py'),     FALSE, v_th),
    (v_eq1, (SELECT id FROM sim.usuario WHERE email='apoyo1@simulador.py'),       FALSE, v_ops);

    -- Equipo 2: Ñandutí Express
    INSERT INTO sim.equipo_miembro (equipo_id, usuario_id, es_capitan, area_id) VALUES
    (v_eq2, (SELECT id FROM sim.usuario WHERE email='capitan2@simulador.py'),     TRUE,  NULL),
    (v_eq2, (SELECT id FROM sim.usuario WHERE email='finanzas2@simulador.py'),    FALSE, v_fin),
    (v_eq2, (SELECT id FROM sim.usuario WHERE email='operaciones2@simulador.py'), FALSE, v_ops),
    (v_eq2, (SELECT id FROM sim.usuario WHERE email='comercial2@simulador.py'),   FALSE, v_com),
    (v_eq2, (SELECT id FROM sim.usuario WHERE email='talento2@simulador.py'),     FALSE, v_th),
    (v_eq2, (SELECT id FROM sim.usuario WHERE email='apoyo2@simulador.py'),       FALSE, v_fin);

    -- Equipo 3: Cerro Corá Trading
    INSERT INTO sim.equipo_miembro (equipo_id, usuario_id, es_capitan, area_id) VALUES
    (v_eq3, (SELECT id FROM sim.usuario WHERE email='capitan3@simulador.py'),     TRUE,  NULL),
    (v_eq3, (SELECT id FROM sim.usuario WHERE email='finanzas3@simulador.py'),    FALSE, v_fin),
    (v_eq3, (SELECT id FROM sim.usuario WHERE email='operaciones3@simulador.py'), FALSE, v_ops),
    (v_eq3, (SELECT id FROM sim.usuario WHERE email='comercial3@simulador.py'),   FALSE, v_com),
    (v_eq3, (SELECT id FROM sim.usuario WHERE email='talento3@simulador.py'),     FALSE, v_th),
    (v_eq3, (SELECT id FROM sim.usuario WHERE email='apoyo3@simulador.py'),       FALSE, v_com);

    -- Equipo 4: Ypacaraí Retail
    INSERT INTO sim.equipo_miembro (equipo_id, usuario_id, es_capitan, area_id) VALUES
    (v_eq4, (SELECT id FROM sim.usuario WHERE email='capitan4@simulador.py'),     TRUE,  NULL),
    (v_eq4, (SELECT id FROM sim.usuario WHERE email='finanzas4@simulador.py'),    FALSE, v_fin),
    (v_eq4, (SELECT id FROM sim.usuario WHERE email='operaciones4@simulador.py'), FALSE, v_ops),
    (v_eq4, (SELECT id FROM sim.usuario WHERE email='comercial4@simulador.py'),   FALSE, v_com),
    (v_eq4, (SELECT id FROM sim.usuario WHERE email='talento4@simulador.py'),     FALSE, v_th),
    (v_eq4, (SELECT id FROM sim.usuario WHERE email='apoyo4@simulador.py'),       FALSE, v_th);
END $$;

-- =============================================================================
-- 5. TRIMESTRES (Q0 inicial + Q1 procesado + Q2 procesado + Q3 abierto + Q4 pendiente)
-- =============================================================================
DO $$
DECLARE
    v_comp_id BIGINT := (SELECT id FROM sim.competencia WHERE codigo = 'RTL-2026A');
    v_now TIMESTAMPTZ := NOW();
BEGIN
    INSERT INTO sim.trimestre (competencia_id, numero, estado, apertura_at, cierre_at, procesado_at) VALUES
    (v_comp_id, 0, 'PROCESADO',           v_now - INTERVAL '5 months', v_now - INTERVAL '5 months', v_now - INTERVAL '5 months'),
    (v_comp_id, 1, 'PROCESADO',           v_now - INTERVAL '4 months', v_now - INTERVAL '3 months 15 days', v_now - INTERVAL '3 months 15 days'),
    (v_comp_id, 2, 'PROCESADO',           v_now - INTERVAL '3 months', v_now - INTERVAL '1 month 15 days', v_now - INTERVAL '1 month 15 days'),
    (v_comp_id, 3, 'ABIERTO_DECISIONES',  v_now - INTERVAL '1 month',  NULL, NULL),
    (v_comp_id, 4, 'PENDIENTE',           NULL, NULL, NULL);
END $$;

-- =============================================================================
-- 6. SNAPSHOTS Q0 INICIO — Condiciones iniciales iguales para todos
-- =============================================================================
DO $$
DECLARE
    v_eq RECORD;
    v_q0 BIGINT := (SELECT t.id FROM sim.trimestre t
                     JOIN sim.competencia c ON t.competencia_id = c.id
                     WHERE c.codigo = 'RTL-2026A' AND t.numero = 0);
BEGIN
    FOR v_eq IN SELECT id FROM sim.equipo WHERE competencia_id = (SELECT id FROM sim.competencia WHERE codigo = 'RTL-2026A')
    LOOP
        INSERT INTO sim.snapshot_estado (
            equipo_id, trimestre_id, momento,
            caja, deuda, patrimonio_neto, valor_planta,
            capacidad, headcount, salario, inventario,
            brand_equity, calidad_percibida, id_acumulado, pip
        ) VALUES (
            v_eq.id, v_q0, 'INICIO',
            500000000,    -- Gs 500M caja
            0,            -- sin deuda
            3000000000,   -- patrimonio Gs 3.000M
            2500000000,   -- planta Gs 2.500M
            50000,        -- 50k capacidad
            100,          -- 100 empleados
            3500000,      -- salario Gs 3.5M
            0,            -- sin inventario
            50.00,        -- brand equity inicial
            50.00,        -- calidad percibida inicial
            0,            -- I+D acumulado
            50.00         -- PIP base
        );
    END LOOP;
END $$;

-- =============================================================================
-- 7. DECISIONES Q1 — Estrategias diferenciadas por equipo
-- =============================================================================
-- Guaraní Market:   Estrategia de precio agresivo, poco marketing
-- Ñandutí Express:  Equilibrada, buen marketing
-- Cerro Corá:       Premium (precio alto, mucho I+D)
-- Ypacaraí Retail:  Volumen (producción máxima, precio bajo)
DO $$
DECLARE
    v_eq1 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Guaraní Market');
    v_eq2 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Ñandutí Express');
    v_eq3 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Cerro Corá Trading');
    v_eq4 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Ypacaraí Retail');
    v_q1  BIGINT := (SELECT t.id FROM sim.trimestre t
                     JOIN sim.competencia c ON t.competencia_id = c.id
                     WHERE c.codigo = 'RTL-2026A' AND t.numero = 1);
BEGIN
    -- Guaraní Market Q1: precio bajo, poco marketing, sin I+D
    INSERT INTO sim.decision_equipo (
        equipo_id, trimestre_id, registrado_por_usuario_id,
        prestamo_solicitado, dividendos_pagar,
        produccion_planificada, compra_mp, inversion_capacidad,
        precio_venta, inversion_marketing,
        contrataciones_netas, aumento_salarial_pct, inversion_capacitacion,
        inversion_id, estado, submitted_at
    ) VALUES (
        v_eq1, v_q1, (SELECT id FROM sim.usuario WHERE email='capitan1@simulador.py'),
        0, 0,
        48000, 48000, 0,
        22000, 15000000,
        0, 0.0000, 5000000,
        0, 'PROCESADA', NOW() - INTERVAL '3 months 20 days'
    );

    -- Ñandutí Express Q1: precio medio, buen marketing, algo de I+D
    INSERT INTO sim.decision_equipo (
        equipo_id, trimestre_id, registrado_por_usuario_id,
        prestamo_solicitado, dividendos_pagar,
        produccion_planificada, compra_mp, inversion_capacidad,
        precio_venta, inversion_marketing,
        contrataciones_netas, aumento_salarial_pct, inversion_capacitacion,
        inversion_id, estado, submitted_at
    ) VALUES (
        v_eq2, v_q1, (SELECT id FROM sim.usuario WHERE email='capitan2@simulador.py'),
        0, 0,
        45000, 45000, 0,
        25000, 30000000,
        5, 0.0200, 8000000,
        20000000, 'PROCESADA', NOW() - INTERVAL '3 months 18 days'
    );

    -- Cerro Corá Q1: precio premium, I+D alto, poco volumen
    INSERT INTO sim.decision_equipo (
        equipo_id, trimestre_id, registrado_por_usuario_id,
        prestamo_solicitado, dividendos_pagar,
        produccion_planificada, compra_mp, inversion_capacidad,
        precio_venta, inversion_marketing,
        contrataciones_netas, aumento_salarial_pct, inversion_capacitacion,
        inversion_id, estado, submitted_at
    ) VALUES (
        v_eq3, v_q1, (SELECT id FROM sim.usuario WHERE email='capitan3@simulador.py'),
        100000000, 0,
        40000, 40000, 0,
        30000, 25000000,
        0, 0.0300, 10000000,
        50000000, 'PROCESADA', NOW() - INTERVAL '3 months 16 days'
    );

    -- Ypacaraí Retail Q1: precio agresivamente bajo, producción máxima
    INSERT INTO sim.decision_equipo (
        equipo_id, trimestre_id, registrado_por_usuario_id,
        prestamo_solicitado, dividendos_pagar,
        produccion_planificada, compra_mp, inversion_capacidad,
        precio_venta, inversion_marketing,
        contrataciones_netas, aumento_salarial_pct, inversion_capacitacion,
        inversion_id, estado, submitted_at
    ) VALUES (
        v_eq4, v_q1, (SELECT id FROM sim.usuario WHERE email='capitan4@simulador.py'),
        0, 0,
        50000, 50000, 50000000,
        20000, 20000000,
        10, 0.0000, 3000000,
        5000000, 'PROCESADA', NOW() - INTERVAL '3 months 22 days'
    );
END $$;

-- =============================================================================
-- 8. RESULTADOS Q1 — Calculados con las fórmulas del motor
-- Demanda base Q1: 200.000 × estacionalidad 0.95 = 190.000 uds
-- =============================================================================
DO $$
DECLARE
    v_eq1 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Guaraní Market');
    v_eq2 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Ñandutí Express');
    v_eq3 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Cerro Corá Trading');
    v_eq4 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Ypacaraí Retail');
    v_q1  BIGINT := (SELECT t.id FROM sim.trimestre t
                     JOIN sim.competencia c ON t.competencia_id = c.id
                     WHERE c.codigo = 'RTL-2026A' AND t.numero = 1);
BEGIN
    -- Guaraní Market Q1: Precio bajo gana share alto
    INSERT INTO sim.resultado_calculo (
        equipo_id, trimestre_id,
        utilizacion_capacidad, factor_eficiencia, produccion_real,
        demanda_total_mercado, demanda_asignada, competitividad, share, ventas_unidades,
        ingresos,
        costo_mp_total, costo_laboral, costo_fijo, costo_marketing, costo_id,
        costo_capacitacion, costo_almacenamiento, depreciacion, intereses, costos_operativos_total,
        utilidad_operativa, utilidad_antes_impuestos, impuesto_ire, utilidad_neta, pip_trimestre
    ) VALUES (
        v_eq1, v_q1,
        0.9600, 0.9800, 47040,
        190000, 57000, 1.1850, 0.30000, 47040,
        1034880000,
        384000000, 455625000, 150000000, 15000000, 0,
        5000000, 0, 125000000, 0, 1134625000,
        -99745000, -99745000, 0, -99745000, 42.50
    );

    -- Ñandutí Express Q1: Equilibrada, buen resultado
    INSERT INTO sim.resultado_calculo (
        equipo_id, trimestre_id,
        utilizacion_capacidad, factor_eficiencia, produccion_real,
        demanda_total_mercado, demanda_asignada, competitividad, share, ventas_unidades,
        ingresos,
        costo_mp_total, costo_laboral, costo_fijo, costo_marketing, costo_id,
        costo_capacitacion, costo_almacenamiento, depreciacion, intereses, costos_operativos_total,
        utilidad_operativa, utilidad_antes_impuestos, impuesto_ire, utilidad_neta, pip_trimestre
    ) VALUES (
        v_eq2, v_q1,
        0.9000, 0.9800, 44100,
        190000, 49400, 1.0200, 0.26000, 44100,
        1102500000,
        360000000, 487068750, 150000000, 30000000, 20000000,
        8000000, 0, 125000000, 0, 1180068750,
        -77568750, -77568750, 0, -77568750, 45.80
    );

    -- Cerro Corá Q1: Premium, poco volumen pero mejor margen unitario
    INSERT INTO sim.resultado_calculo (
        equipo_id, trimestre_id,
        utilizacion_capacidad, factor_eficiencia, produccion_real,
        demanda_total_mercado, demanda_asignada, competitividad, share, ventas_unidades,
        ingresos,
        costo_mp_total, costo_laboral, costo_fijo, costo_marketing, costo_id,
        costo_capacitacion, costo_almacenamiento, depreciacion, intereses, costos_operativos_total,
        utilidad_operativa, utilidad_antes_impuestos, impuesto_ire, utilidad_neta, pip_trimestre
    ) VALUES (
        v_eq3, v_q1,
        0.8000, 0.9800, 39200,
        190000, 38000, 0.8500, 0.20000, 38000,
        1140000000,
        320000000, 458218750, 150000000, 25000000, 50000000,
        10000000, 1200000, 125000000, 3625000, 1143043750,
        -3043750, -3043750, 0, -3043750, 48.20
    );

    -- Ypacaraí Retail Q1: Volumen alto, margen pequeño
    INSERT INTO sim.resultado_calculo (
        equipo_id, trimestre_id,
        utilizacion_capacidad, factor_eficiencia, produccion_real,
        demanda_total_mercado, demanda_asignada, competitividad, share, ventas_unidades,
        ingresos,
        costo_mp_total, costo_laboral, costo_fijo, costo_marketing, costo_id,
        costo_capacitacion, costo_almacenamiento, depreciacion, intereses, costos_operativos_total,
        utilidad_operativa, utilidad_antes_impuestos, impuesto_ire, utilidad_neta, pip_trimestre
    ) VALUES (
        v_eq4, v_q1,
        1.0000, 0.9500, 47500,
        190000, 45600, 0.9450, 0.24000, 45600,
        912000000,
        400000000, 484550000, 150000000, 20000000, 5000000,
        3000000, 0, 125000000, 0, 1187550000,
        -275550000, -275550000, 0, -275550000, 38.00
    );
END $$;

-- =============================================================================
-- 9. SNAPSHOTS Q1 INICIO + CIERRE
-- =============================================================================
DO $$
DECLARE
    v_eq RECORD;
    v_q0 BIGINT := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2026A' AND t.numero = 0);
    v_q1 BIGINT := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2026A' AND t.numero = 1);
    v_comp_id BIGINT := (SELECT id FROM sim.competencia WHERE codigo = 'RTL-2026A');
BEGIN
    -- Q1 INICIO = Q0 INICIO (todos iguales al arrancar)
    FOR v_eq IN SELECT id FROM sim.equipo WHERE competencia_id = v_comp_id
    LOOP
        INSERT INTO sim.snapshot_estado (
            equipo_id, trimestre_id, momento,
            caja, deuda, patrimonio_neto, valor_planta,
            capacidad, headcount, salario, inventario,
            brand_equity, calidad_percibida, id_acumulado, pip
        ) VALUES (
            v_eq.id, v_q1, 'INICIO',
            500000000, 0, 3000000000, 2500000000,
            50000, 100, 3500000, 0,
            50.00, 50.00, 0, 50.00
        );
    END LOOP;
END $$;

-- Q1 CIERRE — reflejan resultados del Q1
DO $$
DECLARE
    v_q1 BIGINT := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2026A' AND t.numero = 1);
BEGIN
    -- Guaraní Market: perdió caja, sin deuda
    INSERT INTO sim.snapshot_estado (equipo_id, trimestre_id, momento, caja, deuda, patrimonio_neto, valor_planta, capacidad, headcount, salario, inventario, brand_equity, calidad_percibida, id_acumulado, pip)
    VALUES ((SELECT id FROM sim.equipo WHERE nombre_empresa='Guaraní Market'), v_q1, 'CIERRE',
            400255000, 0, 2900255000, 2375000000, 50000, 100, 3500000, 960, 48.50, 50.00, 0, 42.50);

    -- Ñandutí Express: perdió menos, con inversiones
    INSERT INTO sim.snapshot_estado (equipo_id, trimestre_id, momento, caja, deuda, patrimonio_neto, valor_planta, capacidad, headcount, salario, inventario, brand_equity, calidad_percibida, id_acumulado, pip)
    VALUES ((SELECT id FROM sim.equipo WHERE nombre_empresa='Ñandutí Express'), v_q1, 'CIERRE',
            422431250, 0, 2922431250, 2375000000, 50000, 105, 3570000, 900, 52.30, 52.00, 20000000, 45.80);

    -- Cerro Corá: invirtió fuerte en I+D, tiene deuda
    INSERT INTO sim.snapshot_estado (equipo_id, trimestre_id, momento, caja, deuda, patrimonio_neto, valor_planta, capacidad, headcount, salario, inventario, brand_equity, calidad_percibida, id_acumulado, pip)
    VALUES ((SELECT id FROM sim.equipo WHERE nombre_empresa='Cerro Corá Trading'), v_q1, 'CIERRE',
            396956250, 100000000, 2896956250, 2375000000, 50000, 100, 3605000, 1200, 51.00, 54.50, 50000000, 48.20);

    -- Ypacaraí Retail: expandió capacidad, perdió mucha caja
    INSERT INTO sim.snapshot_estado (equipo_id, trimestre_id, momento, caja, deuda, patrimonio_neto, valor_planta, capacidad, headcount, salario, inventario, brand_equity, calidad_percibida, id_acumulado, pip)
    VALUES ((SELECT id FROM sim.equipo WHERE nombre_empresa='Ypacaraí Retail'), v_q1, 'CIERRE',
            224450000, 0, 2724450000, 2425000000, 51000, 110, 3500000, 1900, 49.00, 50.50, 5000000, 38.00);
END $$;

-- =============================================================================
-- 10. DECISIONES Q2 — Ajustes post Q1
-- =============================================================================
DO $$
DECLARE
    v_eq1 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Guaraní Market');
    v_eq2 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Ñandutí Express');
    v_eq3 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Cerro Corá Trading');
    v_eq4 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Ypacaraí Retail');
    v_q2  BIGINT := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2026A' AND t.numero = 2);
BEGIN
    -- Guaraní Market Q2: sube precio un poco, más marketing
    INSERT INTO sim.decision_equipo (
        equipo_id, trimestre_id, registrado_por_usuario_id,
        prestamo_solicitado, dividendos_pagar,
        produccion_planificada, compra_mp, inversion_capacidad,
        precio_venta, inversion_marketing,
        contrataciones_netas, aumento_salarial_pct, inversion_capacitacion,
        inversion_id, estado, submitted_at
    ) VALUES (
        v_eq1, v_q2, (SELECT id FROM sim.usuario WHERE email='capitan1@simulador.py'),
        0, 0,
        47000, 47000, 0,
        24000, 25000000,
        0, 0.0100, 5000000,
        10000000, 'PROCESADA', NOW() - INTERVAL '2 months'
    );

    -- Ñandutí Express Q2: mantiene estrategia ganadora
    INSERT INTO sim.decision_equipo (
        equipo_id, trimestre_id, registrado_por_usuario_id,
        prestamo_solicitado, dividendos_pagar,
        produccion_planificada, compra_mp, inversion_capacidad,
        precio_venta, inversion_marketing,
        contrataciones_netas, aumento_salarial_pct, inversion_capacitacion,
        inversion_id, estado, submitted_at
    ) VALUES (
        v_eq2, v_q2, (SELECT id FROM sim.usuario WHERE email='capitan2@simulador.py'),
        0, 0,
        46000, 46000, 0,
        25000, 35000000,
        3, 0.0150, 10000000,
        25000000, 'PROCESADA', NOW() - INTERVAL '1 month 28 days'
    );

    -- Cerro Corá Q2: baja precio levemente, sigue I+D
    INSERT INTO sim.decision_equipo (
        equipo_id, trimestre_id, registrado_por_usuario_id,
        prestamo_solicitado, dividendos_pagar,
        produccion_planificada, compra_mp, inversion_capacidad,
        precio_venta, inversion_marketing,
        contrataciones_netas, aumento_salarial_pct, inversion_capacitacion,
        inversion_id, estado, submitted_at
    ) VALUES (
        v_eq3, v_q2, (SELECT id FROM sim.usuario WHERE email='capitan3@simulador.py'),
        0, 0,
        42000, 42000, 0,
        28000, 30000000,
        5, 0.0200, 12000000,
        40000000, 'PROCESADA', NOW() - INTERVAL '1 month 25 days'
    );

    -- Ypacaraí Retail Q2: sube precio (aprendió), pide préstamo
    INSERT INTO sim.decision_equipo (
        equipo_id, trimestre_id, registrado_por_usuario_id,
        prestamo_solicitado, dividendos_pagar,
        produccion_planificada, compra_mp, inversion_capacidad,
        precio_venta, inversion_marketing,
        contrataciones_netas, aumento_salarial_pct, inversion_capacitacion,
        inversion_id, estado, submitted_at
    ) VALUES (
        v_eq4, v_q2, (SELECT id FROM sim.usuario WHERE email='capitan4@simulador.py'),
        200000000, 0,
        50000, 50000, 0,
        23000, 25000000,
        0, 0.0100, 5000000,
        10000000, 'PROCESADA', NOW() - INTERVAL '1 month 30 days'
    );
END $$;

-- =============================================================================
-- 11. RESULTADOS Q2 �� Estacionalidad base (1.00), demanda = 200.000
-- =============================================================================
DO $$
DECLARE
    v_eq1 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Guaraní Market');
    v_eq2 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Ñandutí Express');
    v_eq3 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Cerro Corá Trading');
    v_eq4 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Ypacaraí Retail');
    v_q2  BIGINT := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2026A' AND t.numero = 2);
BEGIN
    -- Guaraní Market Q2: mejoró con precio ajustado y más marketing
    INSERT INTO sim.resultado_calculo (
        equipo_id, trimestre_id,
        utilizacion_capacidad, factor_eficiencia, produccion_real,
        demanda_total_mercado, demanda_asignada, competitividad, share, ventas_unidades,
        ingresos,
        costo_mp_total, costo_laboral, costo_fijo, costo_marketing, costo_id,
        costo_capacitacion, costo_almacenamiento, depreciacion, intereses, costos_operativos_total,
        utilidad_operativa, utilidad_antes_impuestos, impuesto_ire, utilidad_neta, pip_trimestre
    ) VALUES (
        v_eq1, v_q2,
        0.9400, 0.9800, 46060,
        200000, 54000, 1.1200, 0.27000, 46060,
        1105440000,
        376000000, 460143750, 150000000, 25000000, 10000000,
        5000000, 0, 118750000, 0, 1144893750,
        -39453750, -39453750, 0, -39453750, 44.80
    );

    -- Ñandutí Express Q2: líder, buena ejecución
    INSERT INTO sim.resultado_calculo (
        equipo_id, trimestre_id,
        utilizacion_capacidad, factor_eficiencia, produccion_real,
        demanda_total_mercado, demanda_asignada, competitividad, share, ventas_unidades,
        ingresos,
        costo_mp_total, costo_laboral, costo_fijo, costo_marketing, costo_id,
        costo_capacitacion, costo_almacenamiento, depreciacion, intereses, costos_operativos_total,
        utilidad_operativa, utilidad_antes_impuestos, impuesto_ire, utilidad_neta, pip_trimestre
    ) VALUES (
        v_eq2, v_q2,
        0.9200, 0.9800, 45080,
        200000, 56000, 1.1500, 0.28000, 45080,
        1127000000,
        368000000, 502372500, 150000000, 35000000, 25000000,
        10000000, 0, 118750000, 0, 1209122500,
        -82122500, -82122500, 0, -82122500, 44.20
    );

    -- Cerro Corá Q2: I+D empieza a rendir, mejor calidad
    INSERT INTO sim.resultado_calculo (
        equipo_id, trimestre_id,
        utilizacion_capacidad, factor_eficiencia, produccion_real,
        demanda_total_mercado, demanda_asignada, competitividad, share, ventas_unidades,
        ingresos,
        costo_mp_total, costo_laboral, costo_fijo, costo_marketing, costo_id,
        costo_capacitacion, costo_almacenamiento, depreciacion, intereses, costos_operativos_total,
        utilidad_operativa, utilidad_antes_impuestos, impuesto_ire, utilidad_neta, pip_trimestre
    ) VALUES (
        v_eq3, v_q2,
        0.8400, 0.9800, 41160,
        200000, 44000, 0.9500, 0.22000, 41160,
        1152480000,
        336000000, 478835000, 150000000, 30000000, 40000000,
        12000000, 0, 118750000, 3625000, 1169210000,
        -16730000, -16730000, 0, -16730000, 49.50
    );

    -- Ypacaraí Retail Q2: préstamo ayuda, ajustó precio
    INSERT INTO sim.resultado_calculo (
        equipo_id, trimestre_id,
        utilizacion_capacidad, factor_eficiencia, produccion_real,
        demanda_total_mercado, demanda_asignada, competitividad, share, ventas_unidades,
        ingresos,
        costo_mp_total, costo_laboral, costo_fijo, costo_marketing, costo_id,
        costo_capacitacion, costo_almacenamiento, depreciacion, intereses, costos_operativos_total,
        utilidad_operativa, utilidad_antes_impuestos, impuesto_ire, utilidad_neta, pip_trimestre
    ) VALUES (
        v_eq4, v_q2,
        0.9804, 0.9600, 47059,
        200000, 46000, 1.0100, 0.23000, 46000,
        1058000000,
        400000000, 493093750, 150000000, 25000000, 10000000,
        5000000, 1059000, 121250000, 7250000, 1212652750,
        -154652750, -154652750, 0, -154652750, 40.50
    );
END $$;

-- =============================================================================
-- 12. SNAPSHOTS Q2 INICIO + CIERRE
-- =============================================================================
DO $$
DECLARE
    v_q2 BIGINT := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2026A' AND t.numero = 2);
BEGIN
    -- Q2 INICIO = Q1 CIERRE
    INSERT INTO sim.snapshot_estado (equipo_id, trimestre_id, momento, caja, deuda, patrimonio_neto, valor_planta, capacidad, headcount, salario, inventario, brand_equity, calidad_percibida, id_acumulado, pip)
    VALUES
    ((SELECT id FROM sim.equipo WHERE nombre_empresa='Guaraní Market'), v_q2, 'INICIO',
     400255000, 0, 2900255000, 2375000000, 50000, 100, 3500000, 960, 48.50, 50.00, 0, 42.50),
    ((SELECT id FROM sim.equipo WHERE nombre_empresa='Ñandutí Express'), v_q2, 'INICIO',
     422431250, 0, 2922431250, 2375000000, 50000, 105, 3570000, 900, 52.30, 52.00, 20000000, 45.80),
    ((SELECT id FROM sim.equipo WHERE nombre_empresa='Cerro Corá Trading'), v_q2, 'INICIO',
     396956250, 100000000, 2896956250, 2375000000, 50000, 100, 3605000, 1200, 51.00, 54.50, 50000000, 48.20),
    ((SELECT id FROM sim.equipo WHERE nombre_empresa='Ypacaraí Retail'), v_q2, 'INICIO',
     224450000, 0, 2724450000, 2425000000, 51000, 110, 3500000, 1900, 49.00, 50.50, 5000000, 38.00);

    -- Q2 CIERRE
    INSERT INTO sim.snapshot_estado (equipo_id, trimestre_id, momento, caja, deuda, patrimonio_neto, valor_planta, capacidad, headcount, salario, inventario, brand_equity, calidad_percibida, id_acumulado, pip)
    VALUES
    ((SELECT id FROM sim.equipo WHERE nombre_empresa='Guaraní Market'), v_q2, 'CIERRE',
     360801250, 0, 2860801250, 2256250000, 50000, 100, 3535000, 0, 50.20, 51.50, 10000000, 44.80),
    ((SELECT id FROM sim.equipo WHERE nombre_empresa='Ñandutí Express'), v_q2, 'CIERRE',
     340308750, 0, 2840308750, 2256250000, 50000, 108, 3623550, 0, 54.10, 54.00, 45000000, 44.20),
    ((SELECT id FROM sim.equipo WHERE nombre_empresa='Cerro Corá Trading'), v_q2, 'CIERRE',
     380226250, 100000000, 2880226250, 2256250000, 50000, 105, 3677100, 0, 52.80, 58.20, 90000000, 49.50),
    ((SELECT id FROM sim.equipo WHERE nombre_empresa='Ypacaraí Retail'), v_q2, 'CIERRE',
     269797250, 200000000, 2769797250, 2303750000, 51000, 110, 3535000, 1059, 48.50, 51.20, 15000000, 40.50);
END $$;

-- =============================================================================
-- 13. SNAPSHOTS Q3 INICIO = Q2 CIERRE
-- =============================================================================
DO $$
DECLARE
    v_q3 BIGINT := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2026A' AND t.numero = 3);
BEGIN
    INSERT INTO sim.snapshot_estado (equipo_id, trimestre_id, momento, caja, deuda, patrimonio_neto, valor_planta, capacidad, headcount, salario, inventario, brand_equity, calidad_percibida, id_acumulado, pip)
    VALUES
    ((SELECT id FROM sim.equipo WHERE nombre_empresa='Guaraní Market'), v_q3, 'INICIO',
     360801250, 0, 2860801250, 2256250000, 50000, 100, 3535000, 0, 50.20, 51.50, 10000000, 44.80),
    ((SELECT id FROM sim.equipo WHERE nombre_empresa='Ñandutí Express'), v_q3, 'INICIO',
     340308750, 0, 2840308750, 2256250000, 50000, 108, 3623550, 0, 54.10, 54.00, 45000000, 44.20),
    ((SELECT id FROM sim.equipo WHERE nombre_empresa='Cerro Corá Trading'), v_q3, 'INICIO',
     380226250, 100000000, 2880226250, 2256250000, 50000, 105, 3677100, 0, 52.80, 58.20, 90000000, 49.50),
    ((SELECT id FROM sim.equipo WHERE nombre_empresa='Ypacaraí Retail'), v_q3, 'INICIO',
     269797250, 200000000, 2769797250, 2303750000, 51000, 110, 3535000, 1059, 48.50, 51.20, 15000000, 40.50);
END $$;

-- =============================================================================
-- 14. RANKINGS Q1 y Q2
-- =============================================================================
DO $$
DECLARE
    v_comp BIGINT := (SELECT id FROM sim.competencia WHERE codigo = 'RTL-2026A');
    v_eq1 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Guaraní Market');
    v_eq2 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Ñandutí Express');
    v_eq3 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Cerro Corá Trading');
    v_eq4 BIGINT := (SELECT id FROM sim.equipo WHERE nombre_empresa = 'Ypacaraí Retail');
    v_q1 BIGINT := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2026A' AND t.numero = 1);
    v_q2 BIGINT := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2026A' AND t.numero = 2);
BEGIN
    -- Rankings Q1 (por PIP)
    INSERT INTO sim.ranking_trimestre (competencia_id, trimestre_id, equipo_id, posicion, pip_acumulado, utilidad_acumulada, caja_actual, share_actual) VALUES
    (v_comp, v_q1, v_eq3, 1, 48.20,   -3043750, 396956250, 0.20000),
    (v_comp, v_q1, v_eq2, 2, 45.80,  -77568750, 422431250, 0.26000),
    (v_comp, v_q1, v_eq1, 3, 42.50,  -99745000, 400255000, 0.30000),
    (v_comp, v_q1, v_eq4, 4, 38.00, -275550000, 224450000, 0.24000);

    -- Rankings Q2 (PIP acumulado = promedio Q1+Q2)
    INSERT INTO sim.ranking_trimestre (competencia_id, trimestre_id, equipo_id, posicion, pip_acumulado, utilidad_acumulada, caja_actual, share_actual) VALUES
    (v_comp, v_q2, v_eq3, 1, 48.85,  -19773750, 380226250, 0.22000),
    (v_comp, v_q2, v_eq2, 2, 45.00, -159691250, 340308750, 0.28000),
    (v_comp, v_q2, v_eq1, 3, 43.65, -139198750, 360801250, 0.27000),
    (v_comp, v_q2, v_eq4, 4, 39.25, -430202750, 269797250, 0.23000);
END $$;

-- =============================================================================
-- 15. EVENTO: Suba de diesel en Q2 (disparado por moderador)
-- =============================================================================
DO $$
DECLARE
    v_comp BIGINT := (SELECT id FROM sim.competencia WHERE codigo = 'RTL-2026A');
    v_q2   BIGINT := (SELECT t.id FROM sim.trimestre t JOIN sim.competencia c ON t.competencia_id = c.id WHERE c.codigo = 'RTL-2026A' AND t.numero = 2);
BEGIN
    INSERT INTO sim.evento_competencia (
        competencia_id, trimestre_id, evento_catalogo_id, origen,
        disparado_por_usuario_id, magnitud_aplicada, duracion_aplicada, justificacion
    ) VALUES (
        v_comp, v_q2,
        (SELECT id FROM sim.evento_catalogo WHERE codigo = 'SUBA_DIESEL_PETROPAR'),
        'MODERADOR',
        (SELECT id FROM sim.usuario WHERE email = 'moderador@simulador.py'),
        0.0800, 2,
        'Petropar anunció aumento del 8% en diesel. Impacta logística de distribución.'
    );
END $$;

-- =============================================================================
-- 16. AUDITORÍA — Eventos de bitácora
-- =============================================================================
DO $$
DECLARE
    v_comp BIGINT := (SELECT id FROM sim.competencia WHERE codigo = 'RTL-2026A');
    v_mod  BIGINT := (SELECT id FROM sim.usuario WHERE email = 'moderador@simulador.py');
BEGIN
    INSERT INTO sim.auditoria_evento (competencia_id, usuario_id, tipo_accion, descripcion, ocurrido_at) VALUES
    (v_comp, v_mod, 'COMPETENCIA_CREADA',       'Se creó la competencia Retail Championship 2026',           NOW() - INTERVAL '6 months'),
    (v_comp, v_mod, 'INSCRIPCION_ABIERTA',      'Se abrió la inscripción para equipos',                     NOW() - INTERVAL '5 months 15 days'),
    (v_comp, v_mod, 'COMPETENCIA_INICIADA',      'Se inició la competencia con 4 equipos inscritos',        NOW() - INTERVAL '5 months'),
    (v_comp, v_mod, 'TRIMESTRE_ABIERTO',         'Se abrió Q1 para recibir decisiones',                     NOW() - INTERVAL '4 months'),
    (v_comp, v_mod, 'TRIMESTRE_CERRADO',         'Se cerró Q1 y se procesaron resultados',                  NOW() - INTERVAL '3 months 15 days'),
    (v_comp, v_mod, 'TRIMESTRE_ABIERTO',         'Se abrió Q2 para recibir decisiones',                     NOW() - INTERVAL '3 months'),
    (v_comp, v_mod, 'EVENTO_DISPARADO',          'Se disparó evento: Suba del precio del diesel (Petropar)', NOW() - INTERVAL '2 months 15 days'),
    (v_comp, v_mod, 'TRIMESTRE_CERRADO',         'Se cerró Q2 y se procesaron resultados',                  NOW() - INTERVAL '1 month 15 days'),
    (v_comp, v_mod, 'TRIMESTRE_ABIERTO',         'Se abrió Q3 para recibir decisiones',                     NOW() - INTERVAL '1 month');
END $$;

-- =============================================================================
-- 17. AUDITORÍA DE DECISIONES — CREADA + ENVIADA por cada decision_equipo
-- Cada decisión tiene 2 registros: cuando se creó (BORRADOR) y cuando se envió
-- =============================================================================
DO $$
DECLARE
    v_dec RECORD;
    v_cap_id BIGINT;
    v_estado_borrador JSONB;
    v_estado_enviada JSONB;
BEGIN
    FOR v_dec IN
        SELECT de.id, de.equipo_id, de.trimestre_id, de.registrado_por_usuario_id,
               de.submitted_at,
               de.prestamo_solicitado, de.dividendos_pagar,
               de.produccion_planificada, de.compra_mp, de.inversion_capacidad,
               de.precio_venta, de.inversion_marketing,
               de.contrataciones_netas, de.aumento_salarial_pct, de.inversion_capacitacion,
               de.inversion_id,
               t.numero AS trimestre_numero
        FROM sim.decision_equipo de
        JOIN sim.trimestre t ON de.trimestre_id = t.id
        JOIN sim.competencia c ON t.competencia_id = c.id
        WHERE c.codigo = 'RTL-2026A'
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

        v_estado_enviada := jsonb_build_object(
            'estado', 'ENVIADA',
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

        -- CREADA: capitán crea el borrador ~3 días antes del envío
        INSERT INTO sim.auditoria_decision (
            decision_equipo_id, usuario_id, accion,
            estado_anterior, estado_nuevo, ip_origen, ocurrido_at
        ) VALUES (
            v_dec.id, v_dec.registrado_por_usuario_id, 'CREADA',
            NULL, v_estado_borrador, '192.168.1.10'::INET,
            v_dec.submitted_at - INTERVAL '3 days'
        );

        -- ENVIADA: capitán envía la decisión final
        INSERT INTO sim.auditoria_decision (
            decision_equipo_id, usuario_id, accion,
            estado_anterior, estado_nuevo, ip_origen, ocurrido_at
        ) VALUES (
            v_dec.id, v_dec.registrado_por_usuario_id, 'ENVIADA',
            v_estado_borrador, v_estado_enviada, '192.168.1.10'::INET,
            v_dec.submitted_at
        );
    END LOOP;
END $$;

-- =============================================================================
-- 18. DECISION_CAMPO_LOG — Cambios por campo realizados por miembros del equipo
-- Simula que cada miembro de área editó sus campos antes del envío
-- =============================================================================
DO $$
DECLARE
    v_dec RECORD;
    v_miembro RECORD;
    v_base_time TIMESTAMPTZ;
BEGIN
    FOR v_dec IN
        SELECT de.id, de.equipo_id, de.trimestre_id, de.submitted_at,
               de.precio_venta, de.inversion_marketing,
               de.produccion_planificada, de.compra_mp, de.inversion_capacidad, de.inversion_id,
               de.prestamo_solicitado, de.dividendos_pagar,
               de.contrataciones_netas, de.aumento_salarial_pct, de.inversion_capacitacion
        FROM sim.decision_equipo de
        JOIN sim.trimestre t ON de.trimestre_id = t.id
        JOIN sim.competencia c ON t.competencia_id = c.id
        WHERE c.codigo = 'RTL-2026A'
        ORDER BY t.numero, de.equipo_id
    LOOP
        v_base_time := v_dec.submitted_at - INTERVAL '2 days';

        -- Finanzas: prestamo_solicitado, dividendos_pagar
        SELECT em.usuario_id INTO v_miembro
        FROM sim.equipo_miembro em
        JOIN sim.area_decision ad ON em.area_id = ad.id
        WHERE em.equipo_id = v_dec.equipo_id AND ad.codigo = 'FINANZAS'
        LIMIT 1;

        IF v_miembro.usuario_id IS NOT NULL THEN
            INSERT INTO sim.decision_campo_log (decision_equipo_id, campo, valor_anterior, valor_nuevo, usuario_id, modificado_at) VALUES
            (v_dec.id, 'prestamo_solicitado', '0', v_dec.prestamo_solicitado::TEXT, v_miembro.usuario_id, v_base_time),
            (v_dec.id, 'dividendos_pagar',    '0', v_dec.dividendos_pagar::TEXT,    v_miembro.usuario_id, v_base_time + INTERVAL '10 minutes');
        END IF;

        -- Operaciones: produccion_planificada, compra_mp, inversion_capacidad, inversion_id
        SELECT em.usuario_id INTO v_miembro
        FROM sim.equipo_miembro em
        JOIN sim.area_decision ad ON em.area_id = ad.id
        WHERE em.equipo_id = v_dec.equipo_id AND ad.codigo = 'OPERACIONES'
        LIMIT 1;

        IF v_miembro.usuario_id IS NOT NULL THEN
            INSERT INTO sim.decision_campo_log (decision_equipo_id, campo, valor_anterior, valor_nuevo, usuario_id, modificado_at) VALUES
            (v_dec.id, 'produccion_planificada', '0', v_dec.produccion_planificada::TEXT, v_miembro.usuario_id, v_base_time + INTERVAL '1 hour'),
            (v_dec.id, 'compra_mp',              '0', v_dec.compra_mp::TEXT,              v_miembro.usuario_id, v_base_time + INTERVAL '1 hour 5 minutes'),
            (v_dec.id, 'inversion_capacidad',    '0', v_dec.inversion_capacidad::TEXT,    v_miembro.usuario_id, v_base_time + INTERVAL '1 hour 10 minutes'),
            (v_dec.id, 'inversion_id',           '0', v_dec.inversion_id::TEXT,           v_miembro.usuario_id, v_base_time + INTERVAL '1 hour 15 minutes');
        END IF;

        -- Comercial: precio_venta, inversion_marketing
        SELECT em.usuario_id INTO v_miembro
        FROM sim.equipo_miembro em
        JOIN sim.area_decision ad ON em.area_id = ad.id
        WHERE em.equipo_id = v_dec.equipo_id AND ad.codigo = 'COMERCIAL'
        LIMIT 1;

        IF v_miembro.usuario_id IS NOT NULL THEN
            INSERT INTO sim.decision_campo_log (decision_equipo_id, campo, valor_anterior, valor_nuevo, usuario_id, modificado_at) VALUES
            (v_dec.id, 'precio_venta',       '0', v_dec.precio_venta::TEXT,       v_miembro.usuario_id, v_base_time + INTERVAL '2 hours'),
            (v_dec.id, 'inversion_marketing', '0', v_dec.inversion_marketing::TEXT, v_miembro.usuario_id, v_base_time + INTERVAL '2 hours 10 minutes');
        END IF;

        -- Talento Humano: contrataciones_netas, aumento_salarial_pct, inversion_capacitacion
        SELECT em.usuario_id INTO v_miembro
        FROM sim.equipo_miembro em
        JOIN sim.area_decision ad ON em.area_id = ad.id
        WHERE em.equipo_id = v_dec.equipo_id AND ad.codigo = 'TALENTO_HUMANO'
        LIMIT 1;

        IF v_miembro.usuario_id IS NOT NULL THEN
            INSERT INTO sim.decision_campo_log (decision_equipo_id, campo, valor_anterior, valor_nuevo, usuario_id, modificado_at) VALUES
            (v_dec.id, 'contrataciones_netas',   '0', v_dec.contrataciones_netas::TEXT,   v_miembro.usuario_id, v_base_time + INTERVAL '3 hours'),
            (v_dec.id, 'aumento_salarial_pct',   '0', v_dec.aumento_salarial_pct::TEXT,   v_miembro.usuario_id, v_base_time + INTERVAL '3 hours 10 minutes'),
            (v_dec.id, 'inversion_capacitacion', '0', v_dec.inversion_capacitacion::TEXT, v_miembro.usuario_id, v_base_time + INTERVAL '3 hours 20 minutes');
        END IF;
    END LOOP;
END $$;
