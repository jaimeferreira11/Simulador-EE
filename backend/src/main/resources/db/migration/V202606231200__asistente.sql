-- asistente_faq: catálogo curado de preguntas frecuentes (editable a futuro por Admin).
CREATE TABLE sim.asistente_faq (
    id             BIGSERIAL PRIMARY KEY,
    pregunta       VARCHAR(300) NOT NULL,
    respuesta      TEXT         NOT NULL,
    keywords       TEXT[]       NOT NULL DEFAULT '{}',
    seccion_manual VARCHAR(200) NOT NULL,
    orden          INT          NOT NULL DEFAULT 0,
    activa         BOOLEAN      NOT NULL DEFAULT TRUE
);

-- asistente_consulta_log: auditoría/uso (mejora de FAQ y métricas).
CREATE TABLE sim.asistente_consulta_log (
    id             BIGSERIAL PRIMARY KEY,
    competencia_id BIGINT      NOT NULL REFERENCES sim.competencia(id) ON DELETE RESTRICT,
    usuario_id     BIGINT      REFERENCES sim.usuario(id) ON DELETE SET NULL,
    pregunta       VARCHAR(500) NOT NULL,
    hubo_match     BOOLEAN     NOT NULL,
    faq_id         BIGINT      REFERENCES sim.asistente_faq(id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO sim.asistente_faq (pregunta, respuesta, keywords, seccion_manual, orden) VALUES
('¿Cómo cargo mis decisiones?',
 'Cuando el trimestre esté abierto, andá a "Decisiones". Vas a ver 9 campos agrupados por bloque (Precio, Producción, Marketing, I+D, Personal, Financiero). Podés guardar en borrador y enviar; mientras el trimestre esté abierto podés editar y reenviar (vale la última versión enviada).',
 ARRAY['decision','decisiones','cargar','enviar','formulario','completar'], 'Paso 4 — Cargar decisiones', 1),
('¿Cuántas decisiones hay por trimestre?',
 'Cada equipo toma 9 decisiones por trimestre. El total a lo largo de la simulación es 9 × la cantidad de trimestres del concurso.',
 ARRAY['cuantas','cantidad','decisiones','trimestre','total'], 'Decisiones y mercado', 2),
('¿Cómo se ordena el ranking?',
 'El ranking se ordena por la utilidad acumulada (la ganancia neta sumada). El PIP es un índice de gestión secundario que solo se usa como desempate cuando dos equipos tienen la misma utilidad acumulada.',
 ARRAY['ranking','ordena','criterio','posicion','gana','ganador','primero'], 'Resultados financieros', 3),
('¿Qué es el PIP?',
 'El PIP es un índice de gestión que combina utilidad, market share, marca y caja. Es un indicador secundario: el ranking NO se ordena por el PIP sino por la utilidad acumulada.',
 ARRAY['pip','puntaje','indice','gestion'], 'Resultados financieros', 4),
('¿Qué es la caja proyectada?',
 'Es una estimación de cómo quedaría tu caja al cierre del trimestre según las decisiones que estás cargando. Es orientativa: el resultado real lo calcula el motor al cerrar.',
 ARRAY['caja','proyectada','proyeccion','estimacion'], 'Resultados financieros', 5),
('¿Puedo cambiar mis decisiones después de enviarlas?',
 'Sí, mientras el trimestre esté abierto podés editar y reenviar las veces que quieras; siempre vale la última versión enviada. Cuando el moderador cierra el trimestre, las decisiones quedan congeladas y no se pueden modificar.',
 ARRAY['cambiar','editar','modificar','reenviar','despues','corregir'], 'Paso 4 — Cargar decisiones', 6),
('¿Qué es la capacidad de planta?',
 'Es el máximo de unidades que tu empresa puede producir en el trimestre (se muestra como uds/trim, con una capacidad diaria aproximada). Producir por encima encarece; muy por debajo deja capacidad ociosa.',
 ARRAY['capacidad','planta','produccion','producir','diaria','maximo'], 'Decisiones y mercado', 7),
('¿Qué son los eventos?',
 'Son hechos del mercado que alteran las reglas del trimestre (suba del diesel, hot sale, crisis cambiaria, etc.). Los dispara el moderador o el sistema y aparecen en "Noticias". Conviene leerlos porque pueden cambiar cuánto pesa el precio o el marketing.',
 ARRAY['evento','eventos','noticia','noticias','diesel','crisis','hot','sale'], 'Eventos y dinámica', 8),
('¿Cómo entro al juego?',
 'Se entra solo por invitación del moderador, que te llega por email. No hay registro propio ni códigos públicos. Desde el email creás tu contraseña y completás tu perfil.',
 ARRAY['entrar','ingresar','acceder','invitacion','registro','contrasena','password'], 'Paso 1 — Aceptar la invitación', 9),
('¿Qué pasa cuando se cierra el trimestre?',
 'El motor procesa todo (toma unos segundos): calcula tus ventas, ganancia, caja y posición en el ranking, y abre automáticamente el siguiente trimestre. Recibís una notificación cuando los resultados están listos.',
 ARRAY['cierre','cerrar','procesa','procesar','resultados','siguiente'], 'Paso 7 — Esperar el cierre', 10);
