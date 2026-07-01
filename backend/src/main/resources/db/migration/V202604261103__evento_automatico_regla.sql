-- =============================================================================
-- V202604261103__evento_automatico_regla.sql
-- Rules for automatic events triggered by player decisions
-- =============================================================================

SET search_path TO sim, public;

-- Rules that define conditions for automatic events
CREATE TABLE sim.evento_automatico_regla (
    id                  BIGSERIAL PRIMARY KEY,
    nombre              VARCHAR(100) NOT NULL,
    descripcion         TEXT,
    condicion_tipo      VARCHAR(50) NOT NULL,
    condicion_umbral    NUMERIC(10,4) NOT NULL,
    condicion_operador  VARCHAR(5) NOT NULL DEFAULT '>'
                        CHECK (condicion_operador IN ('>','<','>=','<=','=')),
    probabilidad        NUMERIC(5,4) NOT NULL DEFAULT 1.0,
    efecto_tipo         VARCHAR(50) NOT NULL,
    efecto_valor        NUMERIC(10,4) NOT NULL,
    duracion_trimestres INT NOT NULL DEFAULT 1,
    activo              BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMPTZ DEFAULT NOW()
);

-- Tracks which auto-events were actually applied
CREATE TABLE sim.evento_automatico_aplicado (
    id                      BIGSERIAL PRIMARY KEY,
    competencia_id          BIGINT NOT NULL,
    equipo_id               BIGINT NOT NULL,
    regla_id                BIGINT NOT NULL REFERENCES sim.evento_automatico_regla(id),
    trimestre_origen        INT NOT NULL,
    trimestre_efecto_inicio INT NOT NULL,
    trimestre_efecto_fin    INT NOT NULL,
    created_at              TIMESTAMPTZ DEFAULT NOW()
);

-- Seed auto-event rules
INSERT INTO sim.evento_automatico_regla
    (nombre, descripcion, condicion_tipo, condicion_umbral, condicion_operador, probabilidad, efecto_tipo, efecto_valor, duracion_trimestres)
VALUES
    -- Huelga: produccion/capacidad > 0.85 AND aumento_salarial_pct <= 0
    ('Huelga',
     'Trabajadores sobrecargados sin aumento salarial declaran huelga parcial. La produccion se reduce un 30%.',
     'PRODUCCION_ALTA_SIN_AUMENTO', 0.8500, '>', 0.6000,
     'PRODUCCION', -0.3000, 1),

    -- Desglose tecnico: produccion/capacidad > 0.90
    ('Desglose tecnico',
     'La maquinaria operando al limite sufre fallos. La capacidad se reduce y los costos fijos aumentan.',
     'PRODUCCION_MUY_ALTA', 0.9000, '>', 0.4000,
     'CAPACIDAD', -0.1000, 1),

    -- Elogio publico: inversion_capacitacion > 0 AND aumento_salarial_pct > 0.05
    ('Elogio publico',
     'Los empleados bien tratados y capacitados generan buena reputacion, atrayendo mas clientes.',
     'CAPACITACION_Y_AUMENTO', 0.0500, '>', 0.7000,
     'DEMANDA_TOTAL', 0.0500, 1),

    -- Malestar laboral: aumento_salarial_pct < -0.03
    ('Malestar laboral',
     'La reduccion salarial genera descontento generalizado. La productividad cae un 15%.',
     'SALARIO_BAJO', -0.0300, '<', 0.5000,
     'PRODUCCION', -0.1500, 1),

    -- Boom de marketing: inversion_marketing > average*1.5 (condicion evaluada en servicio)
    ('Boom de marketing',
     'Una campana de marketing agresiva genera un efecto viral. La demanda del equipo aumenta un 10%.',
     'MARKETING_ALTO', 1.5000, '>', 0.5000,
     'DEMANDA_TOTAL', 0.1000, 1);

-- DOWN
-- DROP TABLE sim.evento_automatico_aplicado;
-- DROP TABLE sim.evento_automatico_regla;
