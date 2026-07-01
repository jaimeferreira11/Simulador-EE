-- =============================================================================
-- V202604261100__escenario_predefinido.sql
-- Tablas para escenarios predefinidos de competencia
-- =============================================================================

SET search_path TO sim, public;

-- Escenario predefinido: plantilla con parametros iniciales
CREATE TABLE sim.escenario_predefinido (
    id              BIGSERIAL PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    descripcion     TEXT,
    num_trimestres  INT NOT NULL,
    caja_inicial    BIGINT NOT NULL,
    capacidad_inicial INT NOT NULL,
    headcount_inicial INT NOT NULL,
    salario_inicial BIGINT NOT NULL,
    inventario_inicial INT NOT NULL DEFAULT 0,
    valor_planta_inicial BIGINT NOT NULL,
    dificultad      VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
                    CHECK (dificultad IN ('FACIL','NORMAL','DIFICIL')),
    activo          BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Eventos que se disparan automaticamente en un escenario
CREATE TABLE sim.escenario_evento (
    id                  BIGSERIAL PRIMARY KEY,
    escenario_id        BIGINT NOT NULL REFERENCES sim.escenario_predefinido(id),
    evento_catalogo_id  BIGINT NOT NULL REFERENCES sim.evento_catalogo(id),
    trimestre_numero    INT NOT NULL,
    UNIQUE(escenario_id, evento_catalogo_id, trimestre_numero)
);

-- DOWN
-- DROP TABLE sim.escenario_evento;
-- DROP TABLE sim.escenario_predefinido;
