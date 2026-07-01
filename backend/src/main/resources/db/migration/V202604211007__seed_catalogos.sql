-- =============================================================================
-- V202604211007__seed_catalogos.sql
-- Datos iniciales: roles, rubro retail, parametros macro y de rubro,
-- eventos del catalogo (12 para MVP: 8 manuales + 4 automaticos)
-- =============================================================================

SET search_path TO sim, public;

-- -----------------------------------------------------------------------------
-- Roles del sistema
-- -----------------------------------------------------------------------------
INSERT INTO sim.rol_usuario (codigo, nombre, descripcion) VALUES
    ('ADMIN_PLATAFORMA', 'Administrador de Plataforma', 'Acceso total: gestion de usuarios, rubros, parametros y competencias'),
    ('MODERADOR',        'Moderador',                   'Profesor/facilitador: crea y administra competencias, dispara eventos'),
    ('JUGADOR',          'Jugador',                     'Estudiante/empleado: participa en equipos, toma decisiones');

-- -----------------------------------------------------------------------------
-- Rubro: Retail de conveniencia (unico para MVP)
-- -----------------------------------------------------------------------------
INSERT INTO sim.rubro (codigo, nombre, descripcion, activo) VALUES
    ('RETAIL_CONV', 'Retail de Conveniencia', 'Cadena de tiendas de conveniencia tipo almacen/minimarket en Paraguay. Productos de consumo masivo, alta rotacion, competencia por precio y ubicacion.', TRUE);

-- -----------------------------------------------------------------------------
-- Parametros macroeconomicos: set base Paraguay 2026
-- Basado en proyecciones BCP y datos del Golden File
-- -----------------------------------------------------------------------------
INSERT INTO sim.parametro_macro (
    nombre_set, vigente_desde,
    inflacion_trim_q1, inflacion_trim_q2, inflacion_trim_q3, inflacion_trim_q4,
    tipo_cambio_q1, tipo_cambio_q2, tipo_cambio_q3, tipo_cambio_q4,
    tpm_anual_q1, tpm_anual_q4,
    salario_minimo_q1, salario_minimo_q4,
    ips_patronal, ips_trabajador, aguinaldo_factor, tasa_ire, iva_general,
    activo
) VALUES (
    'PY_2026_BASE', '2026-01-01',
    0.010000, 0.012000, 0.011000, 0.010000,     -- Inflacion ~4% anual
    7300.00, 7350.00, 7400.00, 7450.00,          -- TC USD/PYG gradual
    0.0600, 0.0600,                               -- TPM BCP 6%
    2680373, 2680373,                              -- SMV 2026 estimado
    0.1650, 0.0900, 0.08333, 0.1000, 0.1000,    -- Cargas sociales PY
    TRUE
);

-- -----------------------------------------------------------------------------
-- Parametros del rubro Retail Conveniencia (calibracion base Golden File)
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
    activo
) VALUES (
    (SELECT id FROM sim.rubro WHERE codigo = 'RETAIL_CONV'),
    'RETAIL_CONV_BASE_2026',
    200000,          -- Demanda base trimestral: 200.000 unidades
    25000,           -- Precio referencia: Gs. 25.000/unidad
    1.500,           -- alpha (elasticidad precio)
    0.500,           -- beta (elasticidad marketing)
    0.400,           -- gamma (elasticidad calidad)
    0.400,           -- w_precio
    0.300,           -- w_marketing
    0.200,           -- w_calidad
    0.100,           -- w_marca
    8000,            -- Costo unitario MP: Gs. 8.000
    0.3000,          -- 30% MP importada
    150000000,       -- Costos fijos: Gs. 150M/trimestre
    0.0500,          -- Depreciacion 5%/trimestre
    50000,           -- Gs. 50.000 por unidad de capacidad
    3500000,         -- Salario promedio sector: Gs. 3.5M/mes
    500,             -- 500 unidades/trimestre por empleado
    50.00,           -- Brand equity inicial: 50
    0.0500,          -- Decay BE 5%/trimestre sin marketing
    TRUE
);

-- -----------------------------------------------------------------------------
-- Catalogo de eventos (12 para MVP)
-- 8 manuales (disparados por moderador) + 4 automaticos
-- -----------------------------------------------------------------------------

-- === EVENTOS MANUALES (moderador) ===

INSERT INTO sim.evento_catalogo (codigo, nombre, descripcion, severidad, tipo_efecto, magnitud_default, duracion_q, requiere_anuncio_previo, activo, override_peso_precio, override_peso_marketing, override_peso_calidad, override_peso_marca) VALUES

-- 1. Suba de diesel (Petropar)
('SUBA_DIESEL_PETROPAR',
 'Suba del precio del diesel (Petropar)',
 'Petropar anuncia un incremento del 8% en el precio del diesel, impactando costos logisticos de toda la cadena de distribucion.',
 'MODERADO', 'COSTO_LOGISTICO', 0.0800, 2, TRUE, TRUE,
 NULL, NULL, NULL, NULL),

-- 2. Corte de energia (ANDE)
('CORTE_ENERGIA_ANDE',
 'Racionamiento electrico por sequia',
 'ANDE decreta cortes programados de energia por bajo nivel de embalses. Los costos fijos de operacion aumentan por uso de generadores.',
 'GRAVE', 'COSTO_FIJO', 0.1500, 1, FALSE, TRUE,
 NULL, NULL, NULL, NULL),

-- 3. Crisis cambiaria
('CRISIS_CAMBIARIA',
 'Depreciacion acelerada del guarani',
 'El guarani se deprecia un 12% frente al dolar en el trimestre, encareciendo todas las importaciones de materia prima.',
 'GRAVE', 'TIPO_CAMBIO', 0.1200, 2, FALSE, TRUE,
 0.500, 0.250, 0.150, 0.100),

-- 4. Hot Sale / Black Friday PY
('HOT_SALE_PY',
 'Hot Sale Paraguay',
 'Evento comercial masivo con descuentos coordinados. La demanda del mercado se dispara temporalmente.',
 'POSITIVO', 'DEMANDA_TOTAL', 0.2000, 1, TRUE, TRUE,
 NULL, NULL, NULL, NULL),

-- 5. Aumento del salario minimo (MTESS)
('AUMENTO_SMV_MTESS',
 'Reajuste del Salario Minimo Vigente',
 'El MTESS decreta un aumento del 5% del salario minimo, impactando el costo laboral de todos los equipos.',
 'MODERADO', 'COSTO_FIJO', 0.0500, 1, TRUE, TRUE,
 NULL, NULL, NULL, NULL),

-- 6. Suba de tasa de interes (BCP)
('SUBA_TASA_BCP',
 'Endurecimiento monetario del BCP',
 'El Banco Central del Paraguay sube la tasa de politica monetaria en 200 puntos basicos para contener la inflacion.',
 'MODERADO', 'TASA_INTERES', 0.0200, 2, FALSE, TRUE,
 NULL, NULL, NULL, NULL),

-- 7. Huelga de transportistas
('HUELGA_TRANSPORTISTAS',
 'Huelga nacional de camioneros',
 'Transportistas bloquean rutas principales por 2 semanas, encareciendo la logistica y retrasando entregas de materia prima.',
 'GRAVE', 'COSTO_LOGISTICO', 0.1000, 1, FALSE, TRUE,
 NULL, NULL, NULL, NULL),

-- 8. FOGAPY (Fondo de Garantia)
('FOGAPY_ACTIVO',
 'Activacion del FOGAPY para PyMEs',
 'El gobierno activa el Fondo de Garantia del Paraguay, reduciendo el costo de financiamiento para las empresas participantes.',
 'POSITIVO', 'TASA_INTERES', -0.0300, 2, TRUE, TRUE,
 NULL, NULL, NULL, NULL);

-- === EVENTOS AUTOMATICOS (disparados por el sistema segun reglas) ===

INSERT INTO sim.evento_catalogo (codigo, nombre, descripcion, severidad, tipo_efecto, magnitud_default, duracion_q, requiere_anuncio_previo, activo, override_peso_precio, override_peso_marketing, override_peso_calidad, override_peso_marca) VALUES

-- 9. Inflacion acelerada (automatico si inflacion > umbral)
('INFLACION_ACELERADA',
 'Aceleracion inflacionaria',
 'La inflacion trimestral supera el 3%, erosionando el poder adquisitivo. La demanda cae y el precio se vuelve mas relevante.',
 'MODERADO', 'DEMANDA_TOTAL', -0.0500, 1, FALSE, TRUE,
 0.500, 0.250, 0.150, 0.100),

-- 10. Boom de consumo (automatico si demanda crece sostenida)
('BOOM_CONSUMO',
 'Expansion del consumo interno',
 'Indicadores muestran crecimiento sostenido del consumo. La demanda base aumenta temporalmente.',
 'POSITIVO', 'DEMANDA_TOTAL', 0.1000, 1, FALSE, TRUE,
 NULL, NULL, NULL, NULL),

-- 11. Escasez de materia prima (automatico por tipo cambio)
('ESCASEZ_MP',
 'Escasez de insumos importados',
 'La depreciacion del guarani genera escasez de materia prima importada, encareciendo los costos de produccion.',
 'MODERADO', 'COSTO_MP', 0.1000, 1, FALSE, TRUE,
 NULL, NULL, NULL, NULL),

-- 12. Competencia informal (automatico si precios muy altos)
('COMPETENCIA_INFORMAL',
 'Crecimiento del comercio informal',
 'Los precios altos del sector formal incentivan la expansion del comercio informal, reduciendo la demanda capturada.',
 'LEVE', 'DEMANDA_TOTAL', -0.0300, 1, FALSE, TRUE,
 0.550, 0.200, 0.150, 0.100);

-- DOWN
-- DELETE FROM sim.evento_catalogo;
-- DELETE FROM sim.parametro_rubro;
-- DELETE FROM sim.parametro_macro;
-- DELETE FROM sim.rubro;
-- DELETE FROM sim.rol_usuario;
