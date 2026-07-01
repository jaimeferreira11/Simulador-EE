-- up
ALTER TABLE sim.competencia
    ADD COLUMN bancarrota_habilitada BOOLEAN DEFAULT FALSE;

ALTER TABLE sim.equipo
    ADD COLUMN en_bancarrota BOOLEAN DEFAULT FALSE,
    ADD COLUMN trimestre_bancarrota INT;

-- down
-- ALTER TABLE sim.competencia DROP COLUMN bancarrota_habilitada;
-- ALTER TABLE sim.equipo DROP COLUMN en_bancarrota;
-- ALTER TABLE sim.equipo DROP COLUMN trimestre_bancarrota;
