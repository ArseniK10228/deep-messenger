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
  JWT="${JWT_SECRET:-$(openssl rand -hex 32)}"
  sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$JWT|" .env
  sed -i 's|^PUBLIC_URL=.*|PUBLIC_URL=https://api.deepdesignpc.online|' .env
  sed -i 's|^FIREBASE_SERVICE_ACCOUNT_PATH=.*|FIREBASE_SERVICE_ACCOUNT_PATH=../secrets/firebase-service-account.json|' .env
  sed -i 's|^DATABASE_URL=.*|DATABASE_URL=postgres://deep:deep@127.0.0.1:5432/deep_messenger|' .env
  echo "Created $REPO/.env"
fi

# Never rotate JWT_SECRET on deploy — would log out all users.

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
LOCK_HASH_FILE="$REPO/server/.package-lock.hash"
CURRENT_HASH=$(sha256sum package-lock.json | awk '{print $1}')
if [[ -f "$LOCK_HASH_FILE" && -d node_modules && "$(cat "$LOCK_HASH_FILE")" == "$CURRENT_HASH" ]]; then
  echo "npm ci skipped (package-lock unchanged)"
else
  npm ci
  echo "$CURRENT_HASH" > "$LOCK_HASH_FILE"
fi
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

echo "==> wait for API on :3002"
ok=0
for i in $(seq 1 30); do
  if curl -fsS --max-time 3 http://127.0.0.1:3002/health >/dev/null 2>&1; then
    ok=1
    break
  fi
  if ! systemctl is-active --quiet deep-messenger; then
    echo "WARN: deep-messenger inactive (attempt $i/30)"
    journalctl -u deep-messenger -n 20 --no-pager || true
  fi
  sleep 2
done
if [ "$ok" -ne 1 ]; then
  echo "ERROR: deep-messenger health check failed"
  systemctl status deep-messenger --no-pager || true
  journalctl -u deep-messenger -n 60 --no-pager || true
  exit 1
fi
curl -fsS http://127.0.0.1:3002/health
echo ""
systemctl is-active deep-messenger
echo "Deep Messenger OK on :3002"
