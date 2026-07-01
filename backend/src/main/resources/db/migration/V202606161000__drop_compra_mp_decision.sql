-- =============================================================================
-- V202606161000__drop_compra_mp_decision.sql
-- Elimina el campo muerto compra_mp de decision_equipo.
--
-- El motor de simulación nunca lee compra_mp: el costo de materia prima se
-- calcula internamente como produccion_real × costo_unit_mp. El campo sólo
-- confundía a los jugadores, por lo que se retira end-to-end. Al estar ignorado
-- por el motor, su eliminación NO altera ningún resultado de simulación.
--
-- El CHECK constraint chk_dec_compra_mp se elimina junto con la columna
-- (DROP COLUMN lo arrastra), pero se baja explícitamente por claridad.
-- =============================================================================

SET search_path TO sim, public;

ALTER TABLE sim.decision_equipo DROP CONSTRAINT IF EXISTS chk_dec_compra_mp;
ALTER TABLE sim.decision_equipo DROP COLUMN IF EXISTS compra_mp;

-- Limpia el campo fantasma compra_mp del catálogo de áreas (OPERACIONES),
-- sembrado en V202604211011. Position-independent: no asume el índice del
-- elemento dentro del array.
UPDATE sim.area_decision
   SET campos = array_remove(campos, 'compra_mp')
 WHERE codigo = 'OPERACIONES';
