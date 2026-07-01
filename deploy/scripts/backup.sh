#!/usr/bin/env bash
set -euo pipefail

# ── Simulador — Script de backup de PostgreSQL ──
#
# Hace un dump de la BD `simulador` desde el container `db` del docker-compose
# de produccion. Usa formato `custom` de pg_dump (mas rapido y permite restore
# selectivo) y comprime con gzip nivel 9.
#
# Uso:
#   ./backup.sh                    # backup con valores por defecto
#   BACKUP_DIR=/mnt/backups ./backup.sh
#   RETENTION=30 ./backup.sh
#
# Variables (todas opcionales, con default razonable):
#   COMPOSE_FILE   Ruta al docker-compose.yml (default: ../docker-compose.yml)
#   DB_SERVICE     Nombre del servicio PostgreSQL (default: db)
#   DB_USER        Usuario PostgreSQL (default: simulador)
#   DB_NAME        Nombre de la BD (default: simulador)
#   BACKUP_DIR     Carpeta destino (default: /opt/simulador/backups)
#   RETENTION      Cantidad de backups a conservar (default: 14)
#
# Salida:
#   - Archivos: ${BACKUP_DIR}/simulador_YYYYMMDD_HHMMSS.dump.gz
#   - Logs en stderr
#   - Exit 0 si OK, !=0 si falla
#
# Programar via cron (diario a las 02:00):
#   0 2 * * * /opt/simulador/deploy/scripts/backup.sh >> /var/log/simulador-backup.log 2>&1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Configuracion ──
COMPOSE_FILE="${COMPOSE_FILE:-${SCRIPT_DIR}/../docker-compose.yml}"
DB_SERVICE="${DB_SERVICE:-db}"
DB_USER="${DB_USER:-simulador}"
DB_NAME="${DB_NAME:-simulador}"
BACKUP_DIR="${BACKUP_DIR:-/opt/simulador/backups}"
RETENTION="${RETENTION:-14}"

# ── Helpers de logging (siempre a stderr para no contaminar stdout) ──
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >&2
}

err() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $*" >&2
}

# ── Validaciones previas ──
if [ ! -f "$COMPOSE_FILE" ]; then
    err "No se encuentra docker-compose.yml en: $COMPOSE_FILE"
    exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
    err "docker no esta instalado o no esta en el PATH"
    exit 1
fi

# Crear carpeta destino si no existe (idempotente)
mkdir -p "$BACKUP_DIR"

# ── Verificar que el container este corriendo ──
if ! docker compose -f "$COMPOSE_FILE" ps "$DB_SERVICE" --format json 2>/dev/null | grep -q '"State":"running"'; then
    # Fallback para versiones viejas de docker compose que no soportan --format json
    if ! docker compose -f "$COMPOSE_FILE" ps "$DB_SERVICE" 2>/dev/null | grep -qE "Up|running"; then
        err "El servicio '$DB_SERVICE' no esta corriendo. Levantar con: docker compose up -d"
        exit 2
    fi
fi

# ── Generar nombre de archivo y ejecutar dump ──
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="${BACKUP_DIR}/simulador_${TIMESTAMP}.dump.gz"
TMP_FILE="${BACKUP_FILE}.tmp"

log "Iniciando backup de '$DB_NAME' (servicio=$DB_SERVICE) -> $BACKUP_FILE"

# Pipe: pg_dump dentro del container -> gzip en el host -> archivo temporal
# Usamos archivo .tmp y luego mv para que el destino solo aparezca si todo salio bien
if docker compose -f "$COMPOSE_FILE" exec -T "$DB_SERVICE" \
        pg_dump -U "$DB_USER" -F c --compress=9 "$DB_NAME" \
        | gzip -9 > "$TMP_FILE"; then
    mv "$TMP_FILE" "$BACKUP_FILE"
    SIZE=$(du -h "$BACKUP_FILE" | awk '{print $1}')
    log "Backup OK: $BACKUP_FILE ($SIZE)"
else
    err "pg_dump fallo. Borrando archivo temporal."
    rm -f "$TMP_FILE"
    exit 3
fi

# ── Verificacion minima de integridad ──
# El archivo debe ser legible y no estar vacio
if [ ! -s "$BACKUP_FILE" ]; then
    err "El backup quedo vacio: $BACKUP_FILE"
    rm -f "$BACKUP_FILE"
    exit 4
fi

# Verificar que sea un dump de pg_dump valido descomprimiendo + listando
if ! gunzip -t "$BACKUP_FILE" 2>/dev/null; then
    err "El backup no es un gzip valido: $BACKUP_FILE"
    exit 5
fi

# ── Retencion: borrar backups mas viejos que los ultimos N ──
# Listamos por fecha de modificacion descendente, tomamos los que sobran y los borramos.
log "Aplicando retencion (conservar ultimos $RETENTION backups)"
mapfile -t OLD_BACKUPS < <(
    find "$BACKUP_DIR" -maxdepth 1 -type f -name "simulador_*.dump.gz" -printf '%T@ %p\n' \
        | sort -rn \
        | awk -v keep="$RETENTION" 'NR>keep {print $2}'
)

if [ "${#OLD_BACKUPS[@]}" -gt 0 ]; then
    for old in "${OLD_BACKUPS[@]}"; do
        log "  Borrando viejo: $old"
        rm -f "$old"
    done
else
    log "  Nada para borrar"
fi

log "Backup completado correctamente"
exit 0
