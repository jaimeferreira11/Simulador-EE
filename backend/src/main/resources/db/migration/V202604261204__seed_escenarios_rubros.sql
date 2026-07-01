-- =============================================================================
-- V202604261204__seed_escenarios_rubros.sql
-- One "Normal" scenario per new rubro with sector-appropriate initial values
-- =============================================================================

SET search_path TO sim, public;

-- Manufactura: Mercado Estable (Normal, 4Q, with Boom Construccion Q2)
INSERT INTO sim.escenario_predefinido (nombre, descripcion, num_trimestres, caja_inicial, capacidad_inicial, headcount_inicial, salario_inicial, inventario_inicial, valor_planta_inicial, dificultad, rubro_id)
VALUES ('Mercado Estable - Manufactura',
        'Entorno favorable para la industria. Un boom inmobiliario en Q2 impulsa la demanda. Ideal para aprender las dinamicas del sector industrial paraguayo.',
        4, 800000000, 20000, 80, 4000000, 0, 4000000000, 'NORMAL',
        (SELECT id FROM sim.rubro WHERE codigo = 'MANUFACTURA'));

INSERT INTO sim.escenario_evento (escenario_id, evento_catalogo_id, trimestre_numero)
VALUES (
    (SELECT id FROM sim.escenario_predefinido WHERE nombre = 'Mercado Estable - Manufactura'),
    (SELECT id FROM sim.evento_catalogo WHERE codigo = 'BOOM_CONSTRUCCION'),
    2
);

-- Tecnologia: Despegue Digital (Normal, 4Q, with Transformacion Digital Q2)
INSERT INTO sim.escenario_predefinido (nombre, descripcion, num_trimestres, caja_inicial, capacidad_inicial, headcount_inicial, salario_inicial, inventario_inicial, valor_planta_inicial, dificultad, rubro_id)
VALUES ('Despegue Digital',
        'El gobierno impulsa la transformacion digital en Q2, abriendo oportunidades para empresas tecnologicas. Gestion del talento y la calidad son claves para capitalizar.',
        4, 300000000, 12000, 40, 6000000, 0, 600000000, 'NORMAL',
        (SELECT id FROM sim.rubro WHERE codigo = 'TECNOLOGIA'));

INSERT INTO sim.escenario_evento (escenario_id, evento_catalogo_id, trimestre_numero)
VALUES (
    (SELECT id FROM sim.escenario_predefinido WHERE nombre = 'Despegue Digital'),
    (SELECT id FROM sim.evento_catalogo WHERE codigo = 'TRANSFORMACION_DIGITAL_GOB'),
    2
);

-- Alimentos: Ciclo Estacional (Normal, 4Q, with Fiestas Caacupe Q4)
INSERT INTO sim.escenario_predefinido (nombre, descripcion, num_trimestres, caja_inicial, capacidad_inicial, headcount_inicial, salario_inicial, inventario_inicial, valor_planta_inicial, dificultad, rubro_id)
VALUES ('Ciclo Estacional - Alimentos',
        'Escenario que refleja el ciclo natural del mercado alimenticio paraguayo. Un pico de demanda por fiestas en Q4 premia a quienes planificaron capacidad e inventario.',
        4, 600000000, 75000, 120, 3200000, 0, 3000000000, 'NORMAL',
        (SELECT id FROM sim.rubro WHERE codigo = 'ALIMENTOS'));

INSERT INTO sim.escenario_evento (escenario_id, evento_catalogo_id, trimestre_numero)
VALUES (
    (SELECT id FROM sim.escenario_predefinido WHERE nombre = 'Ciclo Estacional - Alimentos'),
    (SELECT id FROM sim.evento_catalogo WHERE codigo = 'FIESTAS_CAACUPE'),
    4
);

-- DOWN
-- DELETE FROM sim.escenario_evento WHERE escenario_id IN (SELECT id FROM sim.escenario_predefinido WHERE nombre IN ('Mercado Estable - Manufactura','Despegue Digital','Ciclo Estacional - Alimentos'));
-- DELETE FROM sim.escenario_predefinido WHERE nombre IN ('Mercado Estable - Manufactura','Despegue Digital','Ciclo Estacional - Alimentos');
