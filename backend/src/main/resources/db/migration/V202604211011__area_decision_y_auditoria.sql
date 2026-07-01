-- =============================================================================
-- V202604211010__area_decision_y_auditoria.sql
-- Catalogo de areas funcionales, asignacion de area a miembros,
-- y log de auditoria de cambios en decisiones por campo.
-- =============================================================================

SET search_path TO sim, public;

-- -----------------------------------------------------------------------------
-- area_decision: Catalogo global de areas funcionales del equipo
-- Cada area agrupa campos de decision_equipo que sus miembros pueden editar.
-- -----------------------------------------------------------------------------
CREATE TABLE sim.area_decision (
    id              BIGSERIAL       NOT NULL,
    codigo          VARCHAR(40)     NOT NULL,
    nombre          VARCHAR(80)     NOT NULL,
    descripcion     TEXT,
    campos          TEXT[]          NOT NULL,
    orden           SMALLINT        NOT NULL DEFAULT 0,
    activo          BOOLEAN         NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_area_decision          PRIMARY KEY (id),
    CONSTRAINT uk_area_decision_codigo   UNIQUE (codigo)
);

COMMENT ON TABLE sim.area_decision IS 'Catalogo global de areas funcionales. Cada area define que campos de decision_equipo pueden editar sus miembros.';
COMMENT ON COLUMN sim.area_decision.campos IS 'Array de nombres de columna de decision_equipo que pertenecen a esta area';
COMMENT ON COLUMN sim.area_decision.orden IS 'Orden de presentacion en la UI';

-- -----------------------------------------------------------------------------
-- equipo_miembro: agregar area_id (nullable — capitan puede no tener area)
-- -----------------------------------------------------------------------------
ALTER TABLE sim.equipo_miembro
    ADD COLUMN area_id BIGINT,
    ADD CONSTRAINT fk_equipo_miembro_area FOREIGN KEY (area_id)
        REFERENCES sim.area_decision (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_equipo_miembro_area ON sim.equipo_miembro (area_id);

COMMENT ON COLUMN sim.equipo_miembro.area_id IS 'Area funcional asignada. NULL si es capitan sin area especifica. El capitan puede editar cualquier campo independientemente del area.';

-- -----------------------------------------------------------------------------
-- decision_campo_log: Historial de cambios por campo en decisiones
-- Registra quien modifico cada campo, valor anterior y nuevo.
-- -----------------------------------------------------------------------------
CREATE TABLE sim.decision_campo_log (
    id                  BIGSERIAL       NOT NULL,
    decision_equipo_id  BIGINT          NOT NULL,
    campo               VARCHAR(40)     NOT NULL,
    valor_anterior      TEXT,
    valor_nuevo         TEXT            NOT NULL,
    usuario_id          BIGINT          NOT NULL,
    modificado_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_decision_campo_log             PRIMARY KEY (id),
    CONSTRAINT fk_dcl_decision                   FOREIGN KEY (decision_equipo_id)
                                                 REFERENCES sim.decision_equipo (id)
                                                 ON DELETE RESTRICT,
    CONSTRAINT fk_dcl_usuario                    FOREIGN KEY (usuario_id)
                                                 REFERENCES sim.usuario (id)
                                                 ON DELETE SET NULL
);

CREATE INDEX idx_dcl_decision ON sim.decision_campo_log (decision_equipo_id);
CREATE INDEX idx_dcl_usuario  ON sim.decision_campo_log (usuario_id);

COMMENT ON TABLE sim.decision_campo_log IS 'Historial de cambios por campo en decisiones. Permite saber quien del equipo modifico cada valor.';

-- -----------------------------------------------------------------------------
-- Seed: 4 areas funcionales
-- -----------------------------------------------------------------------------
INSERT INTO sim.area_decision (codigo, nombre, descripcion, campos, orden) VALUES
    ('FINANZAS',
     'Finanzas',
     'Gestión financiera: préstamos, dividendos y flujo de caja.',
     ARRAY['prestamo_solicitado', 'dividendos_pagar'],
     1),
    ('OPERACIONES',
     'Operaciones',
     'Producción, logística, capacidad e investigación y desarrollo.',
     ARRAY['produccion_planificada', 'compra_mp', 'inversion_capacidad', 'inversion_id'],
     2),
    ('COMERCIAL',
     'Comercial',
     'Estrategia de precios y marketing.',
     ARRAY['precio_venta', 'inversion_marketing'],
     3),
    ('TALENTO_HUMANO',
     'Talento Humano',
     'Contrataciones, salarios y capacitación del personal.',
     ARRAY['contrataciones_netas', 'aumento_salarial_pct', 'inversion_capacitacion'],
     4);

-- DOWN
-- DROP TABLE IF EXISTS sim.decision_campo_log;
-- ALTER TABLE sim.equipo_miembro DROP COLUMN IF EXISTS area_id;
-- DROP TABLE IF EXISTS sim.area_decision;
