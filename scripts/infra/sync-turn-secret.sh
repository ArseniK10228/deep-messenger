#!/usr/bin/env bash
# Sync coturn secret from VPS → home .env. Run on VPS as root.
set -euo pipefail

HOME_HOST="${HOME_HOST:-root@100.118.211.24}"
HOME_KEY="${HOME_KEY:-/root/.ssh/home_deploy}"
HOME_ENV="${HOME_ENV:-/opt/deep-messenger/.env}"
SECRET_FILE="${SECRET_FILE:-/etc/deep-messenger-turn-secret}"
SSH_OPTS=(-i "$HOME_KEY" -o BatchMode=yes -o StrictHostKeyChecking=no -o ConnectTimeout=15)

if [[ ! -f "$SECRET_FILE" ]]; then
  echo "WARN: $SECRET_FILE missing — skip TURN sync"
  exit 0
fi

SECRET=$(cat "$SECRET_FILE")
echo "==> sync TURN_SECRET to home"

ssh "${SSH_OPTS[@]}" "$HOME_HOST" bash -s <<EOF
set -euo pipefail
ENV_FILE="$HOME_ENV"
touch "\$ENV_FILE"
if grep -q '^TURN_SECRET=' "\$ENV_FILE"; then
  sed -i 's|^TURN_SECRET=.*|TURN_SECRET=$SECRET|' "\$ENV_FILE"
else
  echo 'TURN_SECRET=$SECRET' >> "\$ENV_FILE"
fi
grep -q '^STUN_URLS=' "\$ENV_FILE" || echo 'STUN_URLS=stun:stun.l.google.com:19302' >> "\$ENV_FILE"
grep -q '^TURN_URLS=' "\$ENV_FILE" || echo 'TURN_URLS=turn:turn.deepdesignpc.online:3478?transport=udp,turn:turn.deepdesignpc.online:3478?transport=tcp' >> "\$ENV_FILE"
grep -E '^(TURN_|STUN_)' "\$ENV_FILE"
systemctl restart deep-messenger
sleep 2
systemctl is-active deep-messenger
curl -fsS http://127.0.0.1:3002/health
EOF

echo "TURN sync OK"
