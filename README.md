# Simulador-EE — Plataforma de Simulación Empresarial

Plataforma web de **simulación empresarial competitiva**. Equipos de estudiantes o
empleados administran empresas simuladas durante 4-8 trimestres, en guaraníes,
compitiendo en un mercado común anclado a la realidad económica paraguaya
(IPS, IRE, ANDE, Petropar, salario mínimo MTESS, etc.).

El **motor de simulación es determinista** (misma entrada → misma salida). La IA es
**opcional** y se usa solo para narrativa de eventos y feedback post-trimestre; sin
configurarla, el sistema funciona con textos deterministas.

> Este repositorio contiene el **código fuente completo** (backend, frontend y deploy)
> para que puedas **montar la plataforma por tu cuenta y evaluarla**.

---

## Stack

| Capa | Tecnología | Versión |
|---|---|---|
| Backend | Java + Spring Boot | Java 21, Spring Boot 3.4.4 |
| Frontend | Angular | 19 |
| Base de datos | PostgreSQL | 16+ (esquema `sim`) |
| Migraciones | Flyway | automáticas al arrancar |
| Real-time | Spring WebSocket (STOMP) | — |
| Deploy | Docker Compose + Nginx | — |

---

## Estructura

```
backend/   Aplicación Spring Boot (API REST /v1, WebSocket, motor de simulación)
           └── src/main/resources/db/migration/  Migraciones Flyway + datos demo (seed)
frontend/  Aplicación Angular (dashboards de moderador y jugador)
deploy/    Deploy productivo: docker-compose, Dockerfiles, nginx, backup/restore
           └── DEPLOY.md  Guía de deploy con Docker (producción)
```

---

## Prerequisitos

- **Java 21** (JDK) — Maven incluido vía `./mvnw`
- **Node.js 20+** y npm (para Angular 19)
- **Docker** y **Docker Compose v2**

---

## Arranque en local (desarrollo)

Necesitás tres piezas corriendo: **base de datos**, **backend** y **frontend**.

### 1. Base de datos

```bash
cd backend
docker compose up -d      # Postgres 16 en localhost:5433 (db/user: simulador, pass: simulador_dev)
```

### 2. Backend

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

- Corre en **http://localhost:8080**, context-path **`/v1`** (API en `http://localhost:8080/v1`).
- **Flyway aplica migraciones y carga datos demo automáticamente** — no hay que cargar nada a mano.
- El perfil `dev` es obligatorio pasarlo explícitamente (no hay perfil por defecto) y se conecta a la DB en el puerto **5433**.

### 3. Frontend

```bash
cd frontend
npm install
npm start                 # Angular dev server en http://localhost:4200
```

El dev server proxea `/v1` y `/ws` hacia `http://localhost:8080` (ver `frontend/proxy.conf.json`),
así que en local no hace falta configurar URLs. Abrí **http://localhost:4200**.

### Usuarios demo (seed)

El seed crea usuarios de prueba (contraseña `password123`):

| Usuario | Rol |
|---|---|
| `admin@simulador.py` | Admin Plataforma |
| `moderador@simulador.py` | Moderador |
| `capitan1@simulador.py` … `capitan4@simulador.py` | Jugador (capitán de equipo) |

> Cambiá estas credenciales antes de cualquier uso real.

---

## Configuración (variables de entorno)

En local, los defaults de `dev` funcionan sin configurar nada. Para producción o para
habilitar funciones opcionales, copiá la plantilla y completá:

```bash
cp deploy/.env.example deploy/.env
```

| Variable | Requerida | Descripción |
|---|---|---|
| `DB_PASSWORD` | Sí (prod) | Contraseña de PostgreSQL |
| `JWT_SECRET` | Sí (prod) | Secreto JWT — generar con `openssl rand -base64 48` |
| `APP_BASE_URL` / `CORS_ALLOWED_ORIGINS` | Sí (prod) | Dominio o IP público de la app |
| `HTTP_PORT` | No | Puerto HTTP (default 80) |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | No | SMTP para invitaciones por email. Sin esto, el envío queda deshabilitado. |
| `LLM_PROVIDER` | No | `template` (default, sin IA) · `openai` · `anthropic` · `gemini` |
| `LLM_API_KEY` | No* | *Obligatoria si `LLM_PROVIDER` es un provider real; sin ella cae a modo template. |
| `LLM_MODEL` | No | Modelo del provider; si se omite usa el default de cada uno |

> **La IA es opcional.** Sin `LLM_PROVIDER` (o con `template`) el sistema no requiere ninguna API key.

---

## Tests

```bash
cd backend  && ./mvnw test    # tests del backend
cd frontend && npm test       # tests del frontend
```

---

## Deploy a producción

Todo con Docker Compose. Guía completa en **[`deploy/DEPLOY.md`](deploy/DEPLOY.md)**:

```bash
cp deploy/.env.example deploy/.env   # completar DB_PASSWORD, JWT_SECRET, URLs
cd deploy
docker compose up -d --build         # levanta db + backend + frontend
```

---

## Licencia

Ver [`LICENSE`](LICENSE). Código propietario disponible para **evaluación**; el uso en
producción o con fines comerciales requiere acuerdo con el autor.
