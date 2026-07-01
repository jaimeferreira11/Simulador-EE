# Guia de Deploy — Simulador

## Requisitos del servidor

- Docker 24+ y Docker Compose v2
- 2 GB RAM minimo (4 GB recomendado)
- 10 GB disco
- Puerto 80 (o el que configures)

Para instalar Docker en Ubuntu/Debian:
```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
# cerrar sesion y volver a entrar
```

---

## Deploy con Docker Compose (recomendado)

### 1. Clonar el repo en el servidor

```bash
git clone <tu-repo> /opt/simulador
cd /opt/simulador/deploy
```

### 2. Configurar variables de entorno

```bash
cp .env.example .env
nano .env
```

Variables obligatorias:

| Variable | Descripcion | Ejemplo |
|---|---|---|
| `DB_PASSWORD` | Password de PostgreSQL | `MiPassSeguro2026` |
| `JWT_SECRET` | Secreto para tokens JWT (min 256 bits) | Generar con `openssl rand -base64 48` |
| `APP_BASE_URL` | URL publica del sitio | `http://190.50.100.200` |
| `CORS_ALLOWED_ORIGINS` | Mismo valor que APP_BASE_URL | `http://190.50.100.200` |

Variables opcionales:

| Variable | Default | Descripcion |
|---|---|---|
| `HTTP_PORT` | `80` | Puerto HTTP del frontend |
| `LLM_PROVIDER` | `template` | Provider IA: `openai`, `anthropic`, `gemini`, o `template` (sin IA, default). Con `template` no se requiere API key. |
| `LLM_API_KEY` | _(vacio)_ | API key del proveedor LLM. **Requerida si `LLM_PROVIDER` es real**; sin ella la app arranca pero loguea WARN y la IA cae a modo template/FAQ. |
| `LLM_MODEL` | _(vacio)_ | Modelo a usar. Si se omite, cada provider usa su default: `openai`→`gpt-4o-mini`, `anthropic`→`claude-haiku-4-5-20251001`, `gemini`→`gemini-2.0-flash`. |

### 3. Levantar todo

```bash
./deploy.sh up
```

Esto hace:
1. Levanta PostgreSQL 16
2. Compila el backend (Java 21 + Maven)
3. Ejecuta todas las migraciones Flyway (schema + datos semilla)
4. Compila el frontend (Angular + Node 20)
5. Levanta Nginx como reverse proxy

Primera vez tarda ~3-5 minutos por la compilacion.

### 4. Verificar

```bash
# Ver logs
./deploy.sh logs

# Ver solo backend
./deploy.sh logs backend

# Verificar migraciones
./deploy.sh seed-status
```

Abrir en el navegador: `http://<ip-del-servidor>`

---

## Datos semilla incluidos

Las migraciones de Flyway cargan automaticamente:

| Migracion | Contenido |
|---|---|
| `seed_catalogos` | 3 roles, rubro retail, parametros macro/rubro, 12 eventos catalogo |
| `seed_dev_data` | 1 moderador, 24 jugadores, 1 admin, competencia "Retail Championship 2026" en Q3 con Q1-Q2 procesados |
| `seed_competencia_finalizada` | 1 competencia finalizada con resultados completos |
| `seed_entidades` | Entidades de prueba (universidades, empresas) |
| `seed_escenarios` | Escenarios predefinidos para crear competencias |
| `seed_rubros_nuevos` | Rubros adicionales |
| `seed_eventos_sectoriales` | Eventos por sector |

### Usuarios de prueba

| Email | Password | Rol |
|---|---|---|
| `admin@simulador.py` | `password123` | Admin Plataforma |
| `moderador@simulador.py` | `password123` | Moderador |
| `capitan1@simulador.py` | `password123` | Jugador (Equipo 1) |
| `capitan2@simulador.py` | `password123` | Jugador (Equipo 2) |
| `capitan3@simulador.py` | `password123` | Jugador (Equipo 3) |
| `capitan4@simulador.py` | `password123` | Jugador (Equipo 4) |

Cada equipo tiene 6 jugadores: `capitan`, `finanzas`, `operaciones`, `comercial`, `talento`, `apoyo` + numero de equipo `@simulador.py`.

---

## Comandos utiles

```bash
# Levantar
./deploy.sh up

# Detener
./deploy.sh down

# Ver logs en tiempo real
./deploy.sh logs
./deploy.sh logs backend
./deploy.sh logs db

# Rebuild completo (sin cache)
./deploy.sh rebuild

# Ver migraciones ejecutadas
./deploy.sh seed-status

# Acceder a la BD
docker compose exec db psql -U simulador -d simulador

# Reset completo (borra todos los datos)
./deploy.sh down
docker volume rm deploy_pgdata
./deploy.sh up
```

---

## Deploy sin Docker (servidor propio)

Si preferis instalar directamente sin Docker:

### 1. Instalar dependencias

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install -y openjdk-21-jre-headless postgresql-16 nginx

# Verificar
java -version    # 21+
psql --version   # 16+
nginx -v
```

### 2. Base de datos

```bash
sudo -u postgres psql <<SQL
CREATE USER simulador WITH PASSWORD 'tu_password';
CREATE DATABASE simulador OWNER simulador;
SQL
```

Ejecutar migraciones (manual fallback — normalmente las aplica Flyway al arrancar el backend):
```bash
cd /opt/simulador/backend/src/main/resources/db/migration
for f in $(ls V*.sql | sort); do
    echo ">> $f"
    PGPASSWORD=tu_password psql -U simulador -h localhost -d simulador -f "$f"
done
```

### 3. Backend

```bash
cd /opt/simulador/backend
./mvnw clean package -DskipTests

# Crear servicio systemd
sudo tee /etc/systemd/system/simulador-backend.service > /dev/null <<EOF
[Unit]
Description=Simulador Backend
After=postgresql.service
Requires=postgresql.service

[Service]
Type=simple
User=www-data
WorkingDirectory=/opt/simulador/backend
ExecStart=/usr/bin/java -jar target/simulador-backend-0.1.0-SNAPSHOT.jar
Restart=on-failure
RestartSec=5

Environment=SPRING_PROFILES_ACTIVE=prod
Environment=SPRING_FLYWAY_ENABLED=false
Environment=DB_URL=jdbc:postgresql://localhost:5432/simulador
Environment=DB_USERNAME=simulador
Environment=DB_PASSWORD=tu_password
Environment=JWT_SECRET=tu-jwt-secret-generado-con-openssl
Environment=APP_BASE_URL=http://tu-dominio
Environment=CORS_ALLOWED_ORIGINS=http://tu-dominio

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now simulador-backend
```

### 4. Frontend

```bash
cd /opt/simulador/frontend
npm ci
npx ng build --configuration=production

sudo mkdir -p /var/www/simulador
sudo cp -r dist/frontend/browser/* /var/www/simulador/
```

### 5. Nginx

```bash
sudo tee /etc/nginx/sites-available/simulador > /dev/null <<'EOF'
server {
    listen 80;
    server_name _;

    root /var/www/simulador;
    index index.html;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2?)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    location /v1/ {
        proxy_pass http://127.0.0.1:8080/v1/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /v1/ws/ {
        proxy_pass http://127.0.0.1:8080/v1/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
    }
}
EOF

sudo ln -sf /etc/nginx/sites-available/simulador /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

### 6. Verificar

```bash
sudo systemctl status simulador-backend
curl -s http://localhost:8080/v1/actuator/health
curl -s http://localhost/
```

---

## Actualizar a una nueva version

### Con Docker:
```bash
cd /opt/simulador
git pull
cd deploy
./deploy.sh rebuild
```

### Sin Docker:
```bash
cd /opt/simulador
git pull

# Backend
cd backend
./mvnw clean package -DskipTests
sudo systemctl restart simulador-backend

# Frontend
cd ../frontend
npm ci
npx ng build --configuration=production
sudo cp -r dist/frontend/browser/* /var/www/simulador/

# Migraciones nuevas (si hay) — normalmente las corre Flyway al arrancar el backend
cd ../backend/src/main/resources/db/migration
# ejecutar solo las nuevas V*.sql
```

---

## HTTPS con Let's Encrypt (opcional)

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d tu-dominio.com
# Actualizar APP_BASE_URL y CORS_ALLOWED_ORIGINS a https://
```
