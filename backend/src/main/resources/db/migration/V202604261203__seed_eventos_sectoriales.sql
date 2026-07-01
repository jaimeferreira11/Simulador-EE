-- =============================================================================
-- V202604261203__seed_eventos_sectoriales.sql
-- 15 sector-specific events: 3 RETAIL + 4 MANUFACTURA + 4 TECNOLOGIA + 4 ALIMENTOS
-- =============================================================================

SET search_path TO sim, public;

-- =============================================================================
-- RETAIL_CONV (3 sectoriales)
-- =============================================================================

INSERT INTO sim.evento_catalogo (codigo, nombre, descripcion, severidad, tipo_efecto, magnitud_default, duracion_q, requiere_anuncio_previo, activo, rubro_id,
    override_peso_precio, override_peso_marketing, override_peso_calidad, override_peso_marca) VALUES

('APERTURA_COMPETIDOR_RETAIL',
 'Apertura de cadena internacional',
 'Una cadena de retail internacional anuncia su ingreso al mercado paraguayo. La competencia por precio se intensifica y las marcas locales pierden relevancia frente al nuevo jugador.',
 'GRAVE', 'DEMANDA_TOTAL', -0.1200, 2, TRUE, TRUE,
 (SELECT id FROM sim.rubro WHERE codigo = 'RETAIL_CONV'),
 0.500, 0.300, 0.150, 0.050),

('FERIA_CONSUMO_MASIVO',
 'Feria de Consumo Masivo Asuncion',
 'La Feria anual de Consumo Masivo en Asuncion genera un pico de demanda. Las empresas con mayor presencia de marketing capturan mas visitantes.',
 'POSITIVO', 'DEMANDA_TOTAL', 0.1500, 1, TRUE, TRUE,
 (SELECT id FROM sim.rubro WHERE codigo = 'RETAIL_CONV'),
 0.300, 0.400, 0.200, 0.100),

('REGULACION_PRECIOS_CANASTA',
 'Regulacion de precios de canasta basica',
 'El gobierno decreta precios maximos para productos de canasta basica. Los costos de cumplimiento aumentan y el precio se convierte en el factor dominante del mercado.',
 'GRAVE', 'COSTO_FIJO', 0.0500, 2, TRUE, TRUE,
 (SELECT id FROM sim.rubro WHERE codigo = 'RETAIL_CONV'),
 0.550, 0.200, 0.100, 0.150);

-- =============================================================================
-- MANUFACTURA (4 sectoriales)
-- =============================================================================

INSERT INTO sim.evento_catalogo (codigo, nombre, descripcion, severidad, tipo_efecto, magnitud_default, duracion_q, requiere_anuncio_previo, activo, rubro_id,
    override_peso_precio, override_peso_marketing, override_peso_calidad, override_peso_marca) VALUES

('BOOM_CONSTRUCCION',
 'Boom inmobiliario en Gran Asuncion',
 'El sector inmobiliario experimenta un crecimiento acelerado en el area metropolitana de Asuncion. La demanda de materiales de construccion se dispara.',
 'POSITIVO', 'DEMANDA_TOTAL', 0.2000, 2, TRUE, TRUE,
 (SELECT id FROM sim.rubro WHERE codigo = 'MANUFACTURA'),
 NULL, NULL, NULL, NULL),

('SEQUIA_HIDROELECTRICA',
 'Sequia reduce generacion de Itaipu y Yacyreta',
 'Niveles historicamente bajos en los embalses de Itaipu y Yacyreta obligan a racionamiento energetico. Los costos operativos de las plantas industriales aumentan significativamente.',
 'GRAVE', 'COSTO_FIJO', 0.1500, 2, FALSE, TRUE,
 (SELECT id FROM sim.rubro WHERE codigo = 'MANUFACTURA'),
 NULL, NULL, NULL, NULL),

('ESCASEZ_ACERO_GLOBAL',
 'Escasez global de acero y hierro',
 'Disrupciones en la cadena de suministro global encarecen el acero y hierro importado. Los fabricantes enfrentan costos de materia prima significativamente mayores y la demanda se contrae.',
 'GRAVE', 'COSTO_MP', 0.2000, 3, FALSE, TRUE,
 (SELECT id FROM sim.rubro WHERE codigo = 'MANUFACTURA'),
 NULL, NULL, NULL, NULL),

('LICITACION_PUBLICA',
 'Licitacion de obra publica MOPC',
 'El Ministerio de Obras Publicas abre una licitacion importante para infraestructura vial. La demanda se dispara pero la calidad se vuelve el factor critico para ganar contratos.',
 'POSITIVO', 'DEMANDA_TOTAL', 0.2500, 1, TRUE, TRUE,
 (SELECT id FROM sim.rubro WHERE codigo = 'MANUFACTURA'),
 0.200, 0.200, 0.450, 0.150);

-- =============================================================================
-- TECNOLOGIA (4 sectoriales)
-- =============================================================================

INSERT INTO sim.evento_catalogo (codigo, nombre, descripcion, severidad, tipo_efecto, magnitud_default, duracion_q, requiere_anuncio_previo, activo, rubro_id,
    override_peso_precio, override_peso_marketing, override_peso_calidad, override_peso_marca) VALUES

('TRANSFORMACION_DIGITAL_GOB',
 'Plan de transformacion digital del gobierno',
 'El gobierno paraguayo lanza un ambicioso plan de digitalizacion de servicios publicos a traves del MITIC. Se abren contratos para empresas tecnologicas con soluciones probadas y buena reputacion.',
 'POSITIVO', 'DEMANDA_TOTAL', 0.3000, 2, TRUE, TRUE,
 (SELECT id FROM sim.rubro WHERE codigo = 'TECNOLOGIA'),
 0.150, 0.200, 0.400, 0.250),

('FUGA_TALENTO_IT',
 'Fuga de talento IT al exterior',
 'El boom del nearshoring en la region atrae desarrolladores paraguayos a empresas de Argentina, Uruguay y Brasil. Los costos salariales del sector aumentan significativamente para retener talento.',
 'GRAVE', 'COSTO_FIJO', 0.2000, 3, FALSE, TRUE,
 (SELECT id FROM sim.rubro WHERE codigo = 'TECNOLOGIA'),
 NULL, NULL, NULL, NULL),

('BOOM_FINTECH',
 'Boom de fintech y pagos digitales',
 'El crecimiento de billeteras digitales (Tigo Money, Personal Pay) y la regulacion favorable del BCP impulsan la demanda de soluciones fintech y servicios de pagos.',
 'POSITIVO', 'DEMANDA_TOTAL', 0.1800, 2, TRUE, TRUE,
 (SELECT id FROM sim.rubro WHERE codigo = 'TECNOLOGIA'),
 NULL, NULL, NULL, NULL),

('CIBERATAQUE_SECTOR',
 'Ciberataque masivo afecta confianza del sector',
 'Un ciberataque de alto perfil a instituciones paraguayas genera desconfianza en soluciones tecnologicas. La calidad y seguridad del producto se vuelven los factores dominantes del mercado.',
 'GRAVE', 'DEMANDA_TOTAL', -0.1500, 1, FALSE, TRUE,
 (SELECT id FROM sim.rubro WHERE codigo = 'TECNOLOGIA'),
 0.050, 0.200, 0.450, 0.300);

-- =============================================================================
-- ALIMENTOS (4 sectoriales)
-- =============================================================================

INSERT INTO sim.evento_catalogo (codigo, nombre, descripcion, severidad, tipo_efecto, magnitud_default, duracion_q, requiere_anuncio_previo, activo, rubro_id,
    override_peso_precio, override_peso_marketing, override_peso_calidad, override_peso_marca) VALUES

('SEQUIA_AGRICOLA',
 'Sequia afecta produccion agricola',
 'Una sequia prolongada en la region Oriental reduce la produccion de soja, maiz y otros insumos agricolas. El costo de materia prima para la industria alimenticia se encarece significativamente.',
 'GRAVE', 'COSTO_MP', 0.2500, 2, FALSE, TRUE,
 (SELECT id FROM sim.rubro WHERE codigo = 'ALIMENTOS'),
 NULL, NULL, NULL, NULL),

('FIESTAS_CAACUPE',
 'Temporada Caacupe y fiestas de fin de ano',
 'La peregrinacion a Caacupe (8 de diciembre) y las fiestas navidenas generan un pico extraordinario de consumo de alimentos y bebidas en todo el pais.',
 'POSITIVO', 'DEMANDA_TOTAL', 0.3000, 1, TRUE, TRUE,
 (SELECT id FROM sim.rubro WHERE codigo = 'ALIMENTOS'),
 NULL, NULL, NULL, NULL),

('ALERTA_SANITARIA_SENACSA',
 'Alerta sanitaria de SENACSA',
 'El Servicio Nacional de Calidad y Salud Animal emite una alerta sanitaria que afecta la confianza del consumidor. La calidad y la marca se vuelven factores criticos de decision de compra.',
 'GRAVE', 'DEMANDA_TOTAL', -0.2000, 2, FALSE, TRUE,
 (SELECT id FROM sim.rubro WHERE codigo = 'ALIMENTOS'),
 0.250, 0.100, 0.400, 0.250),

('EXPORTACION_MERCOSUR',
 'Apertura de cuota de exportacion al Mercosur',
 'Paraguay obtiene una cuota preferencial para exportar alimentos procesados a Brasil y Argentina. La demanda crece pero los costos logisticos aumentan por la cadena de distribucion extendida.',
 'POSITIVO', 'DEMANDA_TOTAL', 0.1500, 2, TRUE, TRUE,
 (SELECT id FROM sim.rubro WHERE codigo = 'ALIMENTOS'),
 NULL, NULL, NULL, NULL);

-- DOWN
-- DELETE FROM sim.evento_catalogo WHERE rubro_id IS NOT NULL;
