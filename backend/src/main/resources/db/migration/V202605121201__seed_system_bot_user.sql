-- V202605121201__seed_system_bot_user.sql
-- Usuario reservado para auditar decisiones generadas por bots.
-- No tiene password real, no puede autenticarse, activo=FALSE.

INSERT INTO sim.rol_usuario (codigo, nombre, descripcion)
VALUES ('SYSTEM', 'Sistema', 'Rol reservado para procesos automáticos del sistema')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO sim.usuario (
    email, password_hash, nombre_completo, rol_usuario_id, activo, email_verificado
)
SELECT
    'system-bot@simulador.local',
    '!unusable!',
    'Sistema (bots)',
    r.id,
    FALSE,
    TRUE
FROM sim.rol_usuario r
WHERE r.codigo = 'SYSTEM'
ON CONFLICT (email) DO NOTHING;

-- DOWN
-- DELETE FROM sim.usuario WHERE email = 'system-bot@simulador.local';
-- DELETE FROM sim.rol_usuario WHERE codigo = 'SYSTEM';
