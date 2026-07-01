-- =============================================================================
-- Producto del rubro + Bill of Materials (BOM) de materias primas
-- -----------------------------------------------------------------------------
-- Objetivo: dar a cada rubro un producto concreto y una lista descriptiva de
-- materias primas con sus costos unitarios. Es informacion NARRATIVA: la suma
-- de los costos unitarios del BOM debe ser EXACTAMENTE igual al costo_unit_mp
-- ya parametrizado en sim.parametro_rubro, de modo que el motor de simulacion
-- y el Golden File NO se ven afectados (no se toca parametro_rubro.costo_unit_mp).
--
-- Para RETAIL_CONV el producto es "Bebida embotellada 500 ml" y el costo base
-- es Gs. 8.000 (parametro_rubro.costo_unit_mp = 8000, seed V202604211007).
-- El BOM reparte ese costo segun las proporciones del mockup aprobado, donde el
-- ultimo item absorbe el remanente de redondeo para que la suma sea exacta:
--   Concentrado de fruta  2710  (~33.9%)
--   Azucar                1806  (~22.6%)
--   Envase PET            2323  (~29.0%)
--   Tapa                   645  (~8.1%)
--   Etiqueta               516  (~6.5%)
--   --------------------------------
--   TOTAL                 8000  == costo_unit_mp
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) Columnas descriptivas del producto en sim.rubro (nullable: otros rubros
--    pueden todavia no tener producto definido)
-- -----------------------------------------------------------------------------
ALTER TABLE sim.rubro
    ADD COLUMN producto_nombre      VARCHAR(120),
    ADD COLUMN producto_descripcion TEXT,
    ADD COLUMN unidad_medida        VARCHAR(40);

COMMENT ON COLUMN sim.rubro.producto_nombre      IS 'Nombre del producto concreto que fabrica/vende el rubro (narrativo)';
COMMENT ON COLUMN sim.rubro.producto_descripcion IS 'Descripcion breve del producto (narrativo)';
COMMENT ON COLUMN sim.rubro.unidad_medida        IS 'Unidad de medida del producto (ej. unidad)';

-- -----------------------------------------------------------------------------
-- 2) Tabla de materias primas (BOM) por rubro
-- -----------------------------------------------------------------------------
CREATE TABLE sim.materia_prima_rubro (
    id              BIGSERIAL   NOT NULL,
    rubro_id        BIGINT      NOT NULL,
    nombre          VARCHAR(120) NOT NULL,
    costo_unitario  BIGINT      NOT NULL,
    orden           INT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_materia_prima_rubro            PRIMARY KEY (id),
    CONSTRAINT fk_materia_prima_rubro_rubro      FOREIGN KEY (rubro_id)
                                                 REFERENCES sim.rubro (id) ON DELETE RESTRICT,
    CONSTRAINT ck_materia_prima_rubro_costo_pos  CHECK (costo_unitario >= 0)
);

CREATE INDEX ix_materia_prima_rubro_rubro_id ON sim.materia_prima_rubro (rubro_id);

COMMENT ON TABLE  sim.materia_prima_rubro IS 'Bill of Materials (BOM) narrativo por rubro: la suma de costo_unitario debe igualar parametro_rubro.costo_unit_mp';
COMMENT ON COLUMN sim.materia_prima_rubro.costo_unitario IS 'Costo unitario de la materia prima en guaranies (BIGINT, sin centavos)';
COMMENT ON COLUMN sim.materia_prima_rubro.orden          IS 'Orden de presentacion en la UI';

-- -----------------------------------------------------------------------------
-- 3) Seed RETAIL_CONV: producto + BOM (suma exacta = 8000 = costo_unit_mp)
-- -----------------------------------------------------------------------------
UPDATE sim.rubro
SET producto_nombre      = 'Bebida embotellada 500 ml',
    producto_descripcion = 'Produccion y venta de bebidas en tiendas de conveniencia.',
    unidad_medida        = 'unidad'
WHERE codigo = 'RETAIL_CONV';

INSERT INTO sim.materia_prima_rubro (rubro_id, nombre, costo_unitario, orden) VALUES
    ((SELECT id FROM sim.rubro WHERE codigo = 'RETAIL_CONV'), 'Concentrado de fruta', 2710, 1),
    ((SELECT id FROM sim.rubro WHERE codigo = 'RETAIL_CONV'), 'Azucar',               1806, 2),
    ((SELECT id FROM sim.rubro WHERE codigo = 'RETAIL_CONV'), 'Envase PET',           2323, 3),
    ((SELECT id FROM sim.rubro WHERE codigo = 'RETAIL_CONV'), 'Tapa',                  645, 4),
    ((SELECT id FROM sim.rubro WHERE codigo = 'RETAIL_CONV'), 'Etiqueta',              516, 5);
