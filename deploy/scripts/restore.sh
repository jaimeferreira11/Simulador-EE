#!/usr/bin/env bash
set -euo pipefail

# ── Simulador — Script de restore de PostgreSQL ──
#
# Restaura un backup generado por backup.sh. ATENCION: esto DROPEA y RECREA
# objetos de la BD `simulador` (--clean --if-exists). Toda la informacion actual
# se PERDERA. Hay confirmacion explicita antes de tocar nada.
#
# Uso:
#   ./restore.sh <archivo_backup>
#   ./restore.sh /opt/simulador/backups/simulador_20260512_020000.dump.gz
#
# Variables (todas opcionales):
#   COMPOSE_FILE     Ruta al docker-compose.yml (default: ../docker-compose.yml)
#   DB_SERVICE       Nombre del servicio PostgreSQL (default: db)
#   BACKEND_SERVICE  Nombre del servicio backend (default: backend)
#   DB_USER          Usuario PostgreSQL (default: simulador)
#   DB_NAME          Nombre de la BD (default: simulador)
#   FORCE            Si =1 saltea la confirmacion interactiva (uso CI/scripts)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

COMPOSE_FILE="${COMPOSE_FILE:-${SCRIPT_DIR}/../docker-compose.yml}"
DB_SERVICE="${DB_SERVICE:-db}"
BACKEND_SERVICE="${BACKEND_SERVICE:-backend}"
DB_USER="${DB_USER:-simulador}"
DB_NAME="${DB_NAME:-simulador}"
FORCE="${FORCE:-0}"

# ── Helpers ──
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >&2
}

err() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $*" >&2
}

usage() {
    echo "Uso: $0 <archivo_backup.dump.gz>" >&2
    echo "Ejemplo: $0 /opt/simulador/backups/simulador_20260512_020000.dump.gz" >&2
    exit 1
}

# ── Validaciones de input ──
if [ "$#" -lt 1 ]; then
    usage
fi

BACKUP_FILE="$1"

if [ ! -f "$BACKUP_FILE" ]; then
    err "No se encuentra el archivo: $BACKUP_FILE"
    exit 1
fi

if [ ! -s "$BACKUP_FILE" ]; then
    err "El archivo esta vacio: $BACKUP_FILE"
    exit 1
fi

if ! gunzip -t "$BACKUP_FILE" 2>/dev/null; then
    err "El archivo no parece ser un gzip valido: $BACKUP_FILE"
    exit 1
fi

if [ ! -f "$COMPOSE_FILE" ]; then
    err "No se encuentra docker-compose.yml en: $COMPOSE_FILE"
    exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
    err "docker no esta instalado o no esta en el PATH"
    exit 1
fi

# ── Confirmacion explicita (a menos que FORCE=1) ──
if [ "$FORCE" != "1" ]; then
    cat >&2 <<EOF

==========================================================
  ATENCION — RESTORE DESTRUCTIVO
==========================================================
  Backup origen : $BACKUP_FILE
  BD destino    : $DB_NAME (servicio: $DB_SERVICE)
  Compose file  : $COMPOSE_FILE

  Esto va a:
    1. DETENER el backend ($BACKEND_SERVICE)
    2. DROPEAR todos los objetos de la BD '$DB_NAME'
    3. Restaurar desde el backup
    4. REINICIAR el backend

  Toda la informacion actual de la BD se PERDERA.
==========================================================

EOF
    read -r -p "Para continuar, escribi exactamente 'YES': " CONFIRM
    if [ "$CONFIRM" != "YES" ]; then
        err "Cancelado por el usuario."
        exit 10
    fi
fi

# ── Verificar que el container de DB este corriendo ──
if ! docker compose -f "$COMPOSE_FILE" ps "$DB_SERVICE" 2>/dev/null | grep -qE "Up|running"; then
    err "El servicio '$DB_SERVICE' no esta corriendo. Levantar con: docker compose up -d $DB_SERVICE"
    exit 2
fi

# ── 1) Detener backend para liberar conexiones ──
log "Deteniendo backend ($BACKEND_SERVICE) para liberar conexiones..."
if docker compose -f "$COMPOSE_FILE" ps "$BACKEND_SERVICE" 2>/dev/null | grep -qE "Up|running"; then
    docker compose -f "$COMPOSE_FILE" stop "$BACKEND_SERVICE"
    log "Backend detenido."
else
    log "Backend ya estaba detenido."
fi

# ── 2) Terminar conexiones residuales antes del DROP ──
log "Terminando conexiones residuales a '$DB_NAME'..."
docker compose -f "$COMPOSE_FILE" exec -T "$DB_SERVICE" psql -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 <<SQL || true
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '${DB_NAME}' AND pid <> pg_backend_pid();
SQL

# ── 3) Restore via pg_restore ──
# --clean --if-exists: dropea objetos existentes antes de recrearlos
# --no-owner --no-privileges: evita problemas si el usuario destino difiere del origen
log "Restaurando dump..."
if gunzip -c "$BACKUP_FILE" | docker compose -f "$COMPOSE_FILE" exec -T "$DB_SERVICE" \
        pg_restore -U "$DB_USER" -d "$DB_NAME" \
        --clean --if-exists \
        --no-owner --no-privileges \
        --exit-on-error; then
    log "pg_restore OK."
else
    RC=$?
    err "pg_restore retorno codigo $RC. Revisar logs antes de reiniciar el backend."
    err "El backend sigue detenido. Para volver a levantarlo manualmente:"
    err "    docker compose -f \"$COMPOSE_FILE\" start $BACKEND_SERVICE"
    exit 3
fi

# ── 4) Reiniciar backend ──
log "Reiniciando backend ($BACKEND_SERVICE)..."
docker compose -f "$COMPOSE_FILE" start "$BACKEND_SERVICE"

log "Restore completado. Verifica el healthcheck del backend:"
log "    curl -sf http://localhost:8080/v1/actuator/health"
exit 0
