#!/usr/bin/env bash
# Запускать на deep-design-server (ssh visuals-ts → sudo -i)
set -euo pipefail

APP_DIR=/opt/deep-messenger
REPO_SRC="${1:-}"

if [[ -z "$REPO_SRC" ]]; then
  echo "Usage: sudo bash scripts/deploy-home.sh /path/to/deep-messenger"
  exit 1
fi

echo "==> sync to $APP_DIR"
mkdir -p "$APP_DIR"
rsync -a --delete \
  --exclude node_modules \
  --exclude server/dist \
  --exclude data \
  --exclude .git \
  "$REPO_SRC/" "$APP_DIR/"

cd "$APP_DIR"

if [[ ! -f .env ]]; then
  cp .env.example .env
  JWT=$(openssl rand -hex 32)
  sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$JWT|" .env
  sed -i 's|^PUBLIC_URL=.*|PUBLIC_URL=https://api.deepdesignpc.online|' .env
  sed -i 's|^FIREBASE_SERVICE_ACCOUNT_PATH=.*|FIREBASE_SERVICE_ACCOUNT_PATH=./secrets/firebase-service-account.json|' .env
  echo "Created .env — check secrets/firebase-service-account.json exists"
fi

echo "==> postgres"
docker compose up -d

echo "==> server"
cd server
npm ci
npm run build
npm run migrate

echo "==> systemd"
cp "$APP_DIR/infra/deep-messenger.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable deep-messenger
systemctl restart deep-messenger

sleep 2
curl -fsS http://127.0.0.1:3002/health && echo
echo "Deep Messenger running on :3002"
