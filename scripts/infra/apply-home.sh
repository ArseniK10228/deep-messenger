#!/usr/bin/env bash
# Deep Messenger — apply on deep-design-server. Run as root.
set -euo pipefail

REPO="${REPO:-/opt/deep-messenger}"
NGINX_AVAILABLE="${NGINX_AVAILABLE:-/etc/nginx/sites-available}"
NGINX_ENABLED="${NGINX_ENABLED:-/etc/nginx/sites-enabled}"

if [[ ! -d "$REPO/server" ]]; then
  echo "ERROR: $REPO not found. Clone repo first."
  exit 1
fi

cd "$REPO"

if [[ ! -f .env ]]; then
  cp .env.example .env
  JWT=$(openssl rand -hex 32)
  sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$JWT|" .env
  sed -i 's|^PUBLIC_URL=.*|PUBLIC_URL=https://api.deepdesignpc.online|' .env
  sed -i 's|^FIREBASE_SERVICE_ACCOUNT_PATH=.*|FIREBASE_SERVICE_ACCOUNT_PATH=./secrets/firebase-service-account.json|' .env
  sed -i 's|^DATABASE_URL=.*|DATABASE_URL=postgres://deep:deep@127.0.0.1:5432/deep_messenger|' .env
  echo "Created $REPO/.env"
fi

if [[ -n "${FIREBASE_SERVICE_ACCOUNT_JSON:-}" ]]; then
  mkdir -p "$REPO/secrets"
  printf '%s' "$FIREBASE_SERVICE_ACCOUNT_JSON" > "$REPO/secrets/firebase-service-account.json"
  chmod 600 "$REPO/secrets/firebase-service-account.json"
fi

if [[ ! -f "$REPO/secrets/firebase-service-account.json" ]]; then
  echo "WARN: secrets/firebase-service-account.json missing — auth will not work"
fi

ensure_postgres() {
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    echo "==> PostgreSQL (docker)"
    docker compose -f "$REPO/docker-compose.yml" up -d
    sed -i 's|^DATABASE_URL=.*|DATABASE_URL=postgres://deep:deep@127.0.0.1:5433/deep_messenger|' "$REPO/.env"
    return
  fi

  echo "==> PostgreSQL (native — docker нет на доме)"
  if ! command -v psql >/dev/null 2>&1; then
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq
    apt-get install -y -qq postgresql postgresql-contrib
    systemctl enable --now postgresql
  fi

  sudo -u postgres psql -v ON_ERROR_STOP=0 -tc "SELECT 1 FROM pg_roles WHERE rolname='deep'" | grep -q 1 \
    || sudo -u postgres psql -c "CREATE USER deep WITH PASSWORD 'deep';"
  sudo -u postgres psql -v ON_ERROR_STOP=0 -tc "SELECT 1 FROM pg_database WHERE datname='deep_messenger'" | grep -q 1 \
    || sudo -u postgres psql -c "CREATE DATABASE deep_messenger OWNER deep;"

  PG_VER=$(ls /etc/postgresql 2>/dev/null | head -1 || true)
  if [[ -n "$PG_VER" ]]; then
    PG_HBA="/etc/postgresql/${PG_VER}/main/pg_hba.conf"
    if [[ -f "$PG_HBA" ]] && ! grep -q 'deep_messenger.*deep.*127.0.0.1' "$PG_HBA"; then
      echo 'host deep_messenger deep 127.0.0.1/32 scram-sha-256' >> "$PG_HBA"
      systemctl reload postgresql
    fi
  fi

  sed -i 's|^DATABASE_URL=.*|DATABASE_URL=postgres://deep:deep@127.0.0.1:5432/deep_messenger|' "$REPO/.env"
}

ensure_postgres

echo "==> server build"
cd "$REPO/server"
npm ci
npm run build
npm run migrate

echo "==> systemd"
install -m 644 "$REPO/infra/deep-messenger.service" /etc/systemd/system/deep-messenger.service
systemctl daemon-reload
systemctl enable deep-messenger
systemctl restart deep-messenger

echo "==> nginx (не трогаем 8443/7777 — только проверка основного конфига)"
# Deep слушает :3002 локально; VPS ходит по Tailscale. Отдельный vhost на 7777 не нужен.
nginx -t
systemctl reload nginx

sleep 2
curl -fsS http://127.0.0.1:3002/health
echo ""
systemctl is-active deep-messenger
echo "Deep Messenger OK on :3002"
