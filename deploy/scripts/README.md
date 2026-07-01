# Scripts de operaciones — Simulador

Carpeta con scripts de mantenimiento del deploy de produccion.

| Script | Proposito |
|--------|-----------|
| `backup.sh` | Backup de PostgreSQL (pg_dump custom + gzip) con retencion |
| `restore.sh` | Restore desde un backup, con confirmacion y stop/start del backend |

Todos los scripts asumen que se ejecutan en el servidor donde corre el `docker-compose.yml` de `deploy/`.

---

## `backup.sh`

Hace un dump consistente de la BD `simulador` desde el container `db` y lo guarda comprimido en `BACKUP_DIR`.

### Uso basico

```bash
# Con valores por defecto
./backup.sh

# Cambiando carpeta destino
BACKUP_DIR=/mnt/backups ./backup.sh

# Conservar 30 backups en vez de 14
RETENTION=30 ./backup.sh
```

### Variables de entorno

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `COMPOSE_FILE` | `../docker-compose.yml` | Ruta al docker-compose.yml |
| `DB_SERVICE` | `db` | Nombre del servicio PostgreSQL |
| `DB_USER` | `simulador` | Usuario PostgreSQL |
| `DB_NAME` | `simulador` | Nombre de la BD |
| `BACKUP_DIR` | `/opt/simulador/backups` | Carpeta destino |
| `RETENTION` | `14` | Cantidad de backups a conservar |

### Programar via cron (diario 02:00)

```bash
# crontab -e
0 2 * * * /opt/simulador/deploy/scripts/backup.sh >> /var/log/simulador-backup.log 2>&1
```

### Verificar integridad de un backup existente

El archivo es un `pg_dump` formato custom comprimido con gzip. Para listar su contenido sin restaurar:

```bash
gunzip -c simulador_20260512_020000.dump.gz | pg_restore --list | head -30
```

Si la lista aparece (cabecera `Archive`, `Database`, etc.) y no hay errores, el backup esta integro.

Para verificar mas a fondo, restaurar a una BD descartable:

```bash
docker run --rm -v $(pwd):/dump -e POSTGRES_PASSWORD=test -d --name pgcheck postgres:16-alpine
gunzip -c simulador_*.dump.gz | docker exec -i pgcheck pg_restore -U postgres -d postgres --clean --if-exists
docker stop pgcheck
```

---

## `restore.sh`

Restaura un backup sobre la BD activa. **Operacion destructiva** — pide confirmacion explicita escribiendo `YES`.

### Uso

```bash
./restore.sh /opt/simulador/backups/simulador_20260512_020000.dump.gz
```

El script:

1. Valida el archivo (existe, no vacio, gzip valido)
2. Pide confirmacion (`YES`)
3. Detiene el container del backend (libera conexiones JDBC)
4. Termina conexiones residuales con `pg_terminate_backend`
5. Restaura con `pg_restore --clean --if-exists --no-owner --no-privileges`
6. Reinicia el backend

### Variables de entorno

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `COMPOSE_FILE` | `../docker-compose.yml` | Ruta al docker-compose.yml |
| `DB_SERVICE` | `db` | Nombre del servicio PostgreSQL |
| `BACKEND_SERVICE` | `backend` | Nombre del servicio backend |
| `DB_USER` | `simulador` | Usuario PostgreSQL |
| `DB_NAME` | `simulador` | Nombre de la BD |
| `FORCE` | `0` | Si =1 saltea la confirmacion (uso CI) |

### Avisos importantes

- **NO se puede deshacer.** Hace backup antes si tenes dudas: `./backup.sh && ./restore.sh ...`.
- Si el `pg_restore` falla a mitad, el script deja el backend detenido para que puedas inspeccionar. Para levantarlo manualmente: `docker compose start backend`.
- Si el backup viene de otra version del schema (Flyway), el backend al reiniciar puede intentar migrar — revisar logs con `docker compose logs -f backend`.

---

## Codigos de salida

| Codigo | Significado |
|--------|-------------|
| 0 | OK |
| 1 | Error de validacion (archivo, compose, docker) |
| 2 | Container no esta corriendo |
| 3 | `pg_dump` o `pg_restore` fallaron |
| 4-5 | Backup vacio o corrupto |
| 10 | Restore cancelado por el usuario |
