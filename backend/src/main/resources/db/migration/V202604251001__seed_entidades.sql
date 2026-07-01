-- =============================================================================
-- V202604251001__seed_entidades.sql
-- Seed: 2 entidades de prueba + vincular competencias existentes
-- =============================================================================

SET search_path TO sim, public;

-- -----------------------------------------------------------------------------
-- Entidades de prueba
-- -----------------------------------------------------------------------------
INSERT INTO sim.entidad (nombre, tipo, descripcion, contacto_nombre, contacto_email, activa) VALUES
(
    'Universidad Nacional de Asunción',
    'UNIVERSIDAD',
    'Facultad de Ciencias Económicas — Cátedra de Simulación de Negocios. Utiliza el simulador como herramienta pedagógica en las materias de Administración Estratégica y Gestión Empresarial.',
    'Prof. Dr. Roberto Cáceres',
    'rcaceres@eco.una.py',
    TRUE
),
(
    'Cámara de Comercio e Industria Franco-Paraguaya',
    'EMPRESA',
    'Programa de capacitación corporativa para PyMEs asociadas. Simulaciones trimestrales como parte del ciclo de formación en gestión empresarial.',
    'Lic. María Fernanda Duarte',
    'mfduarte@ccifp.org.py',
    TRUE
);

-- -----------------------------------------------------------------------------
-- Vincular competencias existentes a las entidades
-- RTL-2026A (EN_CURSO) → Universidad Nacional de Asunción
-- RTL-2025A (FINALIZADA) → Cámara de Comercio
-- -----------------------------------------------------------------------------
UPDATE sim.competencia
SET entidad_id = (SELECT id FROM sim.entidad WHERE nombre = 'Universidad Nacional de Asunción')
WHERE codigo = 'RTL-2026A';

UPDATE sim.competencia
SET entidad_id = (SELECT id FROM sim.entidad WHERE nombre = 'Cámara de Comercio e Industria Franco-Paraguaya')
WHERE codigo = 'RTL-2025A';
