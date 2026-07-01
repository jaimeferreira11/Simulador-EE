-- =============================================================================
-- V202604261202__seed_rubros_nuevos.sql
-- Seed 3 new rubros with calibrated parameters for Paraguay
-- =============================================================================

SET search_path TO sim, public;

-- -----------------------------------------------------------------------------
-- Rubros nuevos
-- -----------------------------------------------------------------------------

INSERT INTO sim.rubro (codigo, nombre, descripcion, activo) VALUES
('MANUFACTURA',
 'Manufactura Industrial',
 'Produccion de materiales de construccion e insumos industriales en Paraguay. Sector capital-intensivo con margenes moderados, alta dependencia de energia hidroelectrica y materia prima importada.',
 TRUE),
('TECNOLOGIA',
 'Tecnologia y Servicios',
 'Desarrollo de software, servicios IT y soluciones digitales en Paraguay. Alto margen, baja dependencia de materia prima fisica, alta inversion en I+D y marketing, talento IT competitivo regionalmente.',
 TRUE),
('ALIMENTOS',
 'Alimentos y Bebidas',
 'Produccion y distribucion de alimentos y bebidas en Paraguay. Alta rotacion, fuerte estacionalidad (pico en fiestas), sensible a precio, componente logistico importante y regulacion sanitaria SENACSA.',
 TRUE);

-- -----------------------------------------------------------------------------
-- Parametro rubro: MANUFACTURA
-- -----------------------------------------------------------------------------

INSERT INTO sim.parametro_rubro (
    rubro_id, codigo,
    demanda_base_trim, precio_referencia,
    elasticidad_precio, elasticidad_marketing, elasticidad_calidad,
    peso_precio, peso_marketing, peso_calidad, peso_marca,
    costo_unit_mp, pct_mp_importada, costos_fijos_trim,
    depreciacion_trim, costo_expansion_capacidad,
    salario_promedio_sector, productividad_empleado,
    brand_equity_inicial, decaimiento_be,
    estacionalidad_q1, estacionalidad_q2, estacionalidad_q3, estacionalidad_q4,
    spread_tasa, activo
) VALUES (
    (SELECT id FROM sim.rubro WHERE codigo = 'MANUFACTURA'),
    'MANUFACTURA_BASE_2026',
    80000,           -- Demanda base: 80.000 unidades (menor volumen, mayor valor)
    120000,          -- Precio referencia: Gs. 120.000/unidad
    1.200,           -- alpha: sensibilidad moderada a precio
    0.400,           -- beta: marketing menos relevante
    0.600,           -- gamma: calidad muy relevante (materiales construccion)
    0.300,           -- w_precio: moderado
    0.200,           -- w_marketing: bajo
    0.350,           -- w_calidad: alto (certificaciones, resistencia)
    0.150,           -- w_marca: reputacion importa
    45000,           -- Costo unitario MP: Gs. 45.000 (acero, cemento, hierro)
    0.4500,          -- 45% MP importada (acero, maquinaria)
    300000000,       -- Costos fijos: Gs. 300M/trim (planta grande, energia)
    0.0400,          -- Depreciacion 4%/trim (maquinaria dura mas)
    200000,          -- Gs. 200.000/unidad capacidad (maquinaria pesada)
    4000000,         -- Salario: Gs. 4M/mes (tecnicos calificados)
    200,             -- 200 unidades/trim por empleado (capital-intensivo)
    50.00,           -- Brand equity inicial
    0.0400,          -- Decay BE 4%/trim (reputacion es estable)
    0.8500,          -- Q1: baja (verano, lluvias, poca construccion)
    1.0500,          -- Q2: temporada seca arranca
    1.1000,          -- Q3: pico construccion (seco, pre-fin-de-ano)
    1.0000,          -- Q4: normal (fiestas bajan actividad)
    0.0700,          -- Spread 7% (sector con mas garantias)
    TRUE
);

-- -----------------------------------------------------------------------------
-- Parametro rubro: TECNOLOGIA
-- -----------------------------------------------------------------------------

INSERT INTO sim.parametro_rubro (
    rubro_id, codigo,
    demanda_base_trim, precio_referencia,
    elasticidad_precio, elasticidad_marketing, elasticidad_calidad,
    peso_precio, peso_marketing, peso_calidad, peso_marca,
    costo_unit_mp, pct_mp_importada, costos_fijos_trim,
    depreciacion_trim, costo_expansion_capacidad,
    salario_promedio_sector, productividad_empleado,
    brand_equity_inicial, decaimiento_be,
    estacionalidad_q1, estacionalidad_q2, estacionalidad_q3, estacionalidad_q4,
    spread_tasa, activo
) VALUES (
    (SELECT id FROM sim.rubro WHERE codigo = 'TECNOLOGIA'),
    'TECNOLOGIA_BASE_2026',
    50000,           -- Demanda base: 50.000 licencias/servicios (menor volumen)
    350000,          -- Precio referencia: Gs. 350.000 (alto margen)
    0.800,           -- alpha: precio poco elastico (diferenciacion)
    0.700,           -- beta: marketing muy elastico
    0.500,           -- gamma: calidad importa (bugs = churn)
    0.150,           -- w_precio: casi irrelevante
    0.350,           -- w_marketing: dominante (awareness, posicionamiento)
    0.300,           -- w_calidad: alto (producto funcional)
    0.200,           -- w_marca: reputacion digital importa mucho
    5000,            -- Costo unitario MP: Gs. 5.000 (licencias cloud, infra)
    0.1500,          -- 15% importada (servidores AWS/GCP en USD)
    80000000,        -- Costos fijos: Gs. 80M/trim (oficina, servidores)
    0.0600,          -- Depreciacion 6%/trim (tech se deprecia rapido)
    15000,           -- Gs. 15.000/unidad capacidad (escalar es barato)
    6000000,         -- Salario: Gs. 6M/mes (developers senior PY)
    800,             -- 800 unidades/trim por empleado (alta productividad)
    50.00,           -- Brand equity inicial
    0.0600,          -- Decay BE 6%/trim (mercado olvida rapido sin marketing)
    1.0000,          -- Q1: normal (proyectos nuevos post-vacaciones)
    1.0500,          -- Q2: leve alza (presupuestos aprobados)
    1.0000,          -- Q3: normal
    0.9500,          -- Q4: baja (vacaciones, freeze de deployments)
    0.1000,          -- Spread 10% (sector riesgoso para bancos PY)
    TRUE
);

-- -----------------------------------------------------------------------------
-- Parametro rubro: ALIMENTOS
-- -----------------------------------------------------------------------------

INSERT INTO sim.parametro_rubro (
    rubro_id, codigo,
    demanda_base_trim, precio_referencia,
    elasticidad_precio, elasticidad_marketing, elasticidad_calidad,
    peso_precio, peso_marketing, peso_calidad, peso_marca,
    costo_unit_mp, pct_mp_importada, costos_fijos_trim,
    depreciacion_trim, costo_expansion_capacidad,
    salario_promedio_sector, productividad_empleado,
    brand_equity_inicial, decaimiento_be,
    estacionalidad_q1, estacionalidad_q2, estacionalidad_q3, estacionalidad_q4,
    spread_tasa, activo
) VALUES (
    (SELECT id FROM sim.rubro WHERE codigo = 'ALIMENTOS'),
    'ALIMENTOS_BASE_2026',
    300000,          -- Demanda base: 300.000 unidades (alta rotacion)
    15000,           -- Precio referencia: Gs. 15.000 (bajo valor unitario)
    1.800,           -- alpha: muy sensible a precio (commodity-like)
    0.400,           -- beta: marketing moderado
    0.300,           -- gamma: calidad basica esperada
    0.450,           -- w_precio: dominante (consumo masivo)
    0.250,           -- w_marketing: moderado
    0.150,           -- w_calidad: basico
    0.150,           -- w_marca: confianza en marca alimenticia
    6000,            -- Costo unitario MP: Gs. 6.000 (materias primas agricolas)
    0.2000,          -- 20% importada (insumos, envases)
    200000000,       -- Costos fijos: Gs. 200M/trim (planta, cadena frio)
    0.0500,          -- Depreciacion 5%/trim
    40000,           -- Gs. 40.000/unidad capacidad
    3200000,         -- Salario: Gs. 3.2M/mes (operarios)
    600,             -- 600 unidades/trim por empleado
    50.00,           -- Brand equity inicial
    0.0500,          -- Decay BE 5%/trim
    0.9000,          -- Q1: baja (post-fiestas, vuelta a clases)
    0.9500,          -- Q2: leve baja (invierno)
    1.0000,          -- Q3: normal (primavera)
    1.2500,          -- Q4: pico (Caacupe, Navidad, Ano Nuevo)
    0.0750,          -- Spread 7.5% (agroindustria tiene garantias)
    TRUE
);

-- DOWN
-- DELETE FROM sim.parametro_rubro WHERE codigo IN ('MANUFACTURA_BASE_2026','TECNOLOGIA_BASE_2026','ALIMENTOS_BASE_2026');
-- DELETE FROM sim.rubro WHERE codigo IN ('MANUFACTURA','TECNOLOGIA','ALIMENTOS');
