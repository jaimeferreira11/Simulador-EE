-- Add PENDIENTE_FINALIZAR to competencia estado CHECK constraint
ALTER TABLE sim.competencia DROP CONSTRAINT chk_comp_estado;
ALTER TABLE sim.competencia ADD CONSTRAINT chk_comp_estado CHECK (estado IN (
    'BORRADOR', 'ABIERTA_INSCRIPCION', 'EN_CURSO', 'PAUSADA',
    'PENDIENTE_FINALIZAR', 'FINALIZADA', 'ARCHIVADA'
));
