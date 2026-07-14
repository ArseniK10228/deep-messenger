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
  sed -i 's|^DATABASE_URL=.*|DATABASE_URL=postgres://deep:deep@127.0.0.1:5433/deep_messenger|' .env
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

echo "==> PostgreSQL (docker)"
if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker not installed on home server"
  exit 1
fi
docker compose -f "$REPO/docker-compose.yml" up -d

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
