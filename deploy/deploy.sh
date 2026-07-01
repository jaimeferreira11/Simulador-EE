#!/usr/bin/env bash
set -euo pipefail

# ── Simulador — Script de deploy ──
# Uso: ./deploy.sh [up|down|logs|rebuild|seed-status]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Verificar .env
if [ ! -f .env ]; then
    echo "ERROR: No existe .env — copiá .env.example y configurá los valores"
    echo "  cp .env.example .env && nano .env"
    exit 1
fi

source .env

# Verificar variables obligatorias
if [ -z "${JWT_SECRET:-}" ] || [[ "$JWT_SECRET" == *"cambiar"* ]]; then
    echo "ERROR: JWT_SECRET no está configurado en .env"
    echo "  Generá uno con: openssl rand -base64 48"
    exit 1
fi

CMD="${1:-up}"

case "$CMD" in
    up)
        echo ">> Levantando servicios..."
        docker compose up -d --build
        echo ""
        echo ">> Esperando que el backend esté listo..."
        for i in $(seq 1 30); do
            if curl -sf http://localhost:8080/v1/actuator/health > /dev/null 2>&1; then
                echo ">> Backend OK"
                break
            fi
            sleep 2
        done
        echo ""
        echo "========================================="
        echo "  Simulador desplegado"
        echo "  Frontend: http://localhost:${HTTP_PORT:-80}"
        echo "  Backend:  http://localhost:8080/v1"
        echo "========================================="
        echo ""
        echo "Usuarios de prueba (del seed):"
        echo "  admin@simulador.py      / password123  (Admin)"
        echo "  moderador@simulador.py  / password123  (Moderador)"
        echo "  capitan1@simulador.py   / password123  (Jugador)"
        ;;
    down)
        echo ">> Deteniendo servicios..."
        docker compose down
        ;;
    logs)
        docker compose logs -f "${2:-}"
        ;;
    rebuild)
        echo ">> Rebuild completo..."
        docker compose down
        docker compose build --no-cache
        docker compose up -d
        ;;
    seed-status)
        echo ">> Verificando migraciones ejecutadas..."
        docker compose exec db psql -U simulador -d simulador -c \
            "SELECT version, description, installed_on FROM sim.flyway_schema_history ORDER BY installed_rank;"
        ;;
    *)
        echo "Uso: $0 [up|down|logs|rebuild|seed-status]"
        exit 1
        ;;
esac
