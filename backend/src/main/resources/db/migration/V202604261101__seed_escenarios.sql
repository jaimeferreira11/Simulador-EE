-- =============================================================================
-- V202604261101__seed_escenarios.sql
-- Escenarios predefinidos para competencias
-- =============================================================================

SET search_path TO sim, public;

-- 1. Estabilidad: facil, 4Q, sin eventos, caja alta
INSERT INTO sim.escenario_predefinido (nombre, descripcion, num_trimestres, caja_inicial, capacidad_inicial, headcount_inicial, salario_inicial, inventario_inicial, valor_planta_inicial, dificultad)
VALUES ('Estabilidad',
        'Escenario ideal para principiantes. Entorno economico estable sin eventos externos. Caja inicial generosa para experimentar con las decisiones sin riesgo de bancarrota.',
        4, 800000000, 50000, 30, 3500000, 0, 500000000, 'FACIL');

-- 2. Crisis Economica: normal, 6Q, crisis cambiaria Q3, FOGAPY Q5
INSERT INTO sim.escenario_predefinido (nombre, descripcion, num_trimestres, caja_inicial, capacidad_inicial, headcount_inicial, salario_inicial, inventario_inicial, valor_planta_inicial, dificultad)
VALUES ('Crisis Economica',
        'La economia paraguaya entra en turbulencia. Una crisis cambiaria golpea en Q3 y el gobierno responde con el FOGAPY en Q5. Requiere gestion financiera cuidadosa.',
        6, 500000000, 50000, 30, 3500000, 0, 500000000, 'NORMAL');

INSERT INTO sim.escenario_evento (escenario_id, evento_catalogo_id, trimestre_numero)
VALUES (
    (SELECT id FROM sim.escenario_predefinido WHERE nombre = 'Crisis Economica'),
    (SELECT id FROM sim.evento_catalogo WHERE codigo = 'CRISIS_CAMBIARIA'),
    3
);

INSERT INTO sim.escenario_evento (escenario_id, evento_catalogo_id, trimestre_numero)
VALUES (
    (SELECT id FROM sim.escenario_predefinido WHERE nombre = 'Crisis Economica'),
    (SELECT id FROM sim.evento_catalogo WHERE codigo = 'FOGAPY_ACTIVO'),
    5
);

-- 3. Boom y Caida: dificil, 8Q, hot sale Q2-Q3, suba diesel Q5, crisis Q7
INSERT INTO sim.escenario_predefinido (nombre, descripcion, num_trimestres, caja_inicial, capacidad_inicial, headcount_inicial, salario_inicial, inventario_inicial, valor_planta_inicial, dificultad)
VALUES ('Boom y Caida',
        'Escenario avanzado de 8 trimestres. Comienza con un boom de consumo (Hot Sale Q2 y Q3) seguido de una suba del diesel en Q5 y una crisis cambiaria en Q7. Simula ciclos economicos reales.',
        8, 500000000, 50000, 30, 3500000, 0, 500000000, 'DIFICIL');

INSERT INTO sim.escenario_evento (escenario_id, evento_catalogo_id, trimestre_numero)
VALUES (
    (SELECT id FROM sim.escenario_predefinido WHERE nombre = 'Boom y Caida'),
    (SELECT id FROM sim.evento_catalogo WHERE codigo = 'HOT_SALE_PY'),
    2
);

INSERT INTO sim.escenario_evento (escenario_id, evento_catalogo_id, trimestre_numero)
VALUES (
    (SELECT id FROM sim.escenario_predefinido WHERE nombre = 'Boom y Caida'),
    (SELECT id FROM sim.evento_catalogo WHERE codigo = 'HOT_SALE_PY'),
    3
);

INSERT INTO sim.escenario_evento (escenario_id, evento_catalogo_id, trimestre_numero)
VALUES (
    (SELECT id FROM sim.escenario_predefinido WHERE nombre = 'Boom y Caida'),
    (SELECT id FROM sim.evento_catalogo WHERE codigo = 'SUBA_DIESEL_PETROPAR'),
    5
);

INSERT INTO sim.escenario_evento (escenario_id, evento_catalogo_id, trimestre_numero)
VALUES (
    (SELECT id FROM sim.escenario_predefinido WHERE nombre = 'Boom y Caida'),
    (SELECT id FROM sim.evento_catalogo WHERE codigo = 'CRISIS_CAMBIARIA'),
    7
);

-- 4. Competencia Intensa: normal, 4Q, sin eventos, caja baja
INSERT INTO sim.escenario_predefinido (nombre, descripcion, num_trimestres, caja_inicial, capacidad_inicial, headcount_inicial, salario_inicial, inventario_inicial, valor_planta_inicial, dificultad)
VALUES ('Competencia Intensa',
        'Caja inicial reducida que obliga a una gestion financiera estricta. Sin eventos externos, pero cada decision cuenta. Ideal para equipos que ya dominan los fundamentos.',
        4, 300000000, 50000, 30, 3500000, 0, 500000000, 'NORMAL');

-- DOWN
-- DELETE FROM sim.escenario_evento;
-- DELETE FROM sim.escenario_predefinido;
