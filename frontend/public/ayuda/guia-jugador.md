# Guía paso a paso para Jugador

*Pensada para alumnos y participantes — sin tecnicismos*

---

## Glosario de términos

Estos son los conceptos que vas a ver en el simulador. Manejalos siempre con el mismo significado.

### Roles

| Término | Significado |
|---|---|
| **Moderador** | El profesor o facilitador. Es quien armó la competencia y te invitó. |
| **Jugador** | Vos. Sos parte de un equipo que maneja una empresa simulada. |
| **Equipo** | El grupo de compañeros con el que vas a competir. Todas las decisiones se toman entre el equipo. |

### Estructura del juego

| Término | Significado |
|---|---|
| **Competencia** | La "partida" completa. Dura varios trimestres y termina con un ranking final. |
| **Trimestre** | Cada período del juego. Es la unidad de tiempo: dura mientras el moderador lo deje abierto. |
| **Rubro** | La industria simulada. En esta versión, todos los equipos venden en una **tienda de conveniencia**. |
| **Ronda** | Otra forma de decir trimestre. Preferimos *"trimestre"*. |

### Decisiones y mercado

| Término | Significado |
|---|---|
| **Decisión** | Cada elección que toma tu equipo (precio, cuánto producir, cuánto invertir en marketing, etc.). Hay **9 por trimestre**. |
| **Precio** | Lo que tu equipo cobra por unidad. Subir el precio sube el margen pero baja la demanda. |
| **Producción** | Cuántas unidades vas a fabricar/comprar para vender. Si producís de menos, perdés ventas; si producís de más, te queda inventario. |
| **Capacidad** | El máximo que tu planta puede producir en el trimestre (se muestra como `uds/trim`). El panel de Operaciones también te muestra una **capacidad diaria** orientativa (≈ capacidad del trimestre ÷ 90 días) para que te hagas una idea del ritmo de producción. |
| **Marketing** | Plata que invertís en publicidad. Más marketing atrae más demanda. |
| **I+D** | Inversión en mejorar la calidad. Es **acumulativa**: lo que invertís hoy sigue beneficiándote en trimestres futuros. |
| **Brand Equity** | Reputación de tu marca. Crece con marketing y calidad, cae si las descuidás. |
| **Market Share** | Tu porcentaje del mercado. Resulta de comparar tus decisiones con las del resto. |
| **Demanda** | Cuánta gente quiere comprarte ese trimestre. |
| **Inventario** | Producto que te sobró sin vender y queda para el próximo trimestre. |

### Resultados financieros

| Término | Significado |
|---|---|
| **Ingresos / Ventas** | Plata que entró por vender. |
| **Costos** | Plata que salió (producción, marketing, salarios, impuestos). |
| **Ganancia / Utilidad** | Ingresos menos costos. Si es negativo, hubo pérdida. |
| **Caja** | Plata disponible en la cuenta del equipo. Si llega a cero, no podés operar. |
| **Caja proyectada** | Estimación de cómo va a quedar tu caja al cierre del trimestre según las decisiones que estás cargando. Es orientativa: el resultado real lo calcula el motor al cerrar. |
| **PIP** | Índice global de desempeño que combina varios factores (utilidad, share, marca, caja). Es un **índice de gestión secundario**: no ordena el ranking, solo desempata si dos equipos terminan con la misma utilidad acumulada. |
| **Ranking** | Tabla de posiciones de todos los equipos, **ordenada por la utilidad acumulada** (gana quien más ganó en total). Se actualiza cada trimestre. |

### Eventos y dinámica

| Término | Significado |
|---|---|
| **Evento** | Algo que pasa en el mercado y altera las reglas del trimestre (suba del diesel, hot sale, crisis cambiaria, etc.). Los disparan el moderador o el sistema. |
| **Noticia** | Texto que te explica un evento cuando ocurre. Aparece en tu sección de novedades. |
| **Feedback** | Texto automático que aparece al cerrar el trimestre, explicándole a tu equipo qué funcionó y qué no. |
| **Determinístico** | El motor no tiene azar: si dos equipos toman exactamente las mismas decisiones, obtienen exactamente los mismos resultados. |
| **Guaraníes (Gs.)** | Toda la plata se expresa en guaraníes, sin centavos. |

---

## Antes de empezar

**¿Qué vas a hacer?**
Vas a manejar, junto con tu equipo, una empresa simulada durante varios trimestres. Cada trimestre tomás decisiones de negocio y al cerrarse ves cómo te fue contra los otros equipos.

**Lo que necesitás:**

- Una invitación del moderador en tu email (sin ella, no podés entrar)
- Acordar con tu equipo cómo se van a coordinar (chat interno, grupo aparte, reuniones)

> 🤖 **Es posible que compitas contra bots.** El moderador puede armar la competencia con algunos equipos jugados automáticamente por el sistema. Los reconocés por el ícono 🤖 en el ranking y los listados. Juegan con estrategias **determinísticas** — no inventan trampas y no ven tus decisiones, pero pueden ser duros rivales según el nivel que elija el moderador (Fácil / Medio / Difícil).

---

## Paso 1 — Aceptar la invitación

Te llega un email del moderador con un link de invitación. Hacé clic.

> ⚠️ **Importante:** **solo entrás por invitación**. No hay forma de registrarse solo ni códigos públicos. Si no te llegó, hablá con tu profesor.

Vas a crear tu contraseña y completar tu perfil.

> *Recomendación:* usá un email que revisás todos los días — las notificaciones del juego van por ahí.

---

## Paso 2 — Entrar y conocer tu equipo

Al entrar caes en **"Mis competencias"**. Elegí la competencia activa y vas a ver:

- El nombre de tu equipo
- Quiénes son tus compañeros
- En qué trimestre está el juego

> *Recomendación:* presentate con tu equipo desde el primer día. Las decisiones se discuten — no son individuales.

---

## Paso 3 — Esperar a que arranque

Si la competencia todavía no empezó, vas a ver el estado **"Abierta para inscripción"** o **"En curso"** sin trimestre abierto aún. Esperá a que el moderador inicie.

> *Tip:* aprovechá para leer la sección de ayuda y familiarizarte con la pantalla de decisiones, así no pierdas tiempo cuando arranque.

En tu tablero vas a encontrar el panel **"Información del Concurso"**, que te dice el rubro, cuántos trimestres dura la competencia, cuántas empresas están compitiendo, cuántas decisiones hay por trimestre y el total de decisiones de toda la simulación, junto con un recordatorio para que completes todas las decisiones.

---

## Paso 4 — Cargar decisiones (corazón del juego)

Cuando se abre un trimestre, andá a **"Decisiones"**. Vas a ver **9 campos** para completar, agrupados en bloques:

| Bloque | Qué decidís |
|---|---|
| **Precio** | A cuánto vendés cada unidad |
| **Producción** | Cuántas unidades fabricar/comprar |
| **Marketing** | Plata para publicidad |
| **I+D** | Plata para mejorar calidad |
| **Personal** | Cantidad de empleados, salarios |
| **Financiero** | Préstamos, inversiones |

**Cómo funciona el guardado:**

- Podés ir cargando y guardar en **borrador** las veces que quieras
- Cuando estés conformes, **enviás las decisiones**
- Mientras el trimestre esté abierto podés **editar y reenviar** todas las veces que quieras — siempre vale la **última versión enviada**

> ⚠️ Cuando el moderador cierra el trimestre, las decisiones quedan **congeladas**. No se puede deshacer.

> *Recomendación:* no dejes para último momento. Reservá tiempo para revisar entre todos antes de enviar.

---

## Paso 5 — Seguir las noticias del trimestre

Mientras el trimestre está abierto, pueden pasar **eventos** (subas de combustible, promociones, crisis cambiaria). Aparecen en **"Noticias"**.

> *Recomendación:* leé cada evento con atención — pueden cambiar las reglas del trimestre (por ejemplo, hacer que el precio importe más o menos en la decisión del cliente).

---

## Paso 6 — Coordinar con tu equipo

Usá el **chat del equipo** dentro de la plataforma para ponerse de acuerdo. Todos los miembros del equipo ven los mismos datos y comparten las mismas decisiones.

> *Recomendación:* definan internamente **quién tiene la última palabra** si no se ponen de acuerdo. La plataforma usa **la última versión enviada**, sin importar quién la mandó.

---

## Paso 7 — Esperar el cierre

Cuando el moderador cierra el trimestre:

- El motor procesa todo (toma unos segundos)
- Calcula tus ventas, ganancia, caja y posición en el ranking
- Abre automáticamente el siguiente trimestre

Recibís una notificación cuando los resultados están listos.

---

## Paso 8 — Revisar resultados

Andá a **"Resultados"**. Vas a ver:

- Ventas, ingresos, costos, ganancia, caja
- Tu market share y posición
- Un **feedback automático** con observaciones pedagógicas

Y en **"Rankings"** ves la tabla con todos los equipos.

> *Recomendación:* leé el feedback con calma. No te apures a cargar el siguiente trimestre sin entender qué pasó en este.

---

## Paso 9 — Discutir en equipo y ajustar estrategia

Antes de tocar el próximo trimestre, juntense (presencial o virtual) y revisen:

- ¿Qué decisión funcionó?
- ¿Cuál no? ¿Por qué?
- ¿Qué van a cambiar?

> *Tip:* mirar **a los líderes** del ranking ayuda — pero no copies sin pensar. Lo que les funcionó en su contexto puede no funcionar en el tuyo.

---

## Paso 10 — Repetir hasta el final

Repetí pasos 4 → 8 cada trimestre hasta el último. Al cerrar el último:

- La competencia pasa a **"Finalizada"**
- Se publica el ranking final
- Podés bajar un **reporte PDF** con todo el recorrido de tu equipo

---

## Resumen de límites importantes

| Concepto | Valor |
|---|---|
| Trimestres por competencia | 4 a 8 |
| Cantidad de equipos | 3 a 12 |
| Decisiones por trimestre | 9 |
| Tiempo para cargar decisiones | **Lo define el moderador** (no es automático) |
| Cambios después del cierre | ❌ No se permite |

## Cosas que **no** podés hacer

- ❌ Estar en dos equipos de la misma competencia
- ❌ Ver las decisiones de los otros equipos antes del cierre
- ❌ Modificar decisiones de un trimestre ya cerrado
- ❌ Auto-registrarte (siempre por invitación)
- ❌ Cambiar de equipo dentro de la misma competencia

## Consejos finales para jugar bien

1. **Tomate en serio el trimestre 1.** Es la base de todo lo que viene. Errores grandes acá son difíciles de revertir.
2. **No cambies todo cada trimestre.** Probá un cambio, mirá el efecto, ajustá. La estrategia se construye, no se inventa cada vez.
3. **No descuides la caja.** Sin caja no podés producir el siguiente trimestre, por más buenas decisiones que tomes.
4. **El I+D se acumula** — invertir un poco cada trimestre suele rendir más que invertir todo de golpe al final.
5. **Leé las noticias.** Los eventos no son decorativos: cambian las reglas y los que los ignoran suelen perder.
6. **Discutan antes de enviar.** El simulador premia decisiones reflexionadas, no rápidas.
