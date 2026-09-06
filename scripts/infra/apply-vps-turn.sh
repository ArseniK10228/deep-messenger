#!/usr/bin/env bash
# coturn on VPS (2.56.120.54). Run as root after DNS turn.deepdesignpc.online → VPS.
set -euo pipefail

REPO="${REPO:-/tmp/deep-messenger}"
CONF_SRC="${REPO}/infra/coturn/turnserver.conf"
TURN_CONF=/etc/turnserver.conf

if [[ ! -f "$CONF_SRC" ]]; then
  echo "ERROR: $CONF_SRC not found"
  exit 1
fi

echo "==> coturn package"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq coturn

mkdir -p /var/log/turnserver

if [[ ! -f /etc/deep-messenger-turn-secret ]]; then
  openssl rand -hex 32 > /etc/deep-messenger-turn-secret
  chmod 600 /etc/deep-messenger-turn-secret
fi
SECRET=$(cat /etc/deep-messenger-turn-secret)

install -m 644 "$CONF_SRC" "$TURN_CONF"
sed -i "s|^static-auth-secret=.*|static-auth-secret=$SECRET|" "$TURN_CONF"
sed -i 's/^#TURNSERVER_ENABLED=1/TURNSERVER_ENABLED=1/' /etc/default/coturn 2>/dev/null || true
grep -q '^TURNSERVER_ENABLED=1' /etc/default/coturn 2>/dev/null || echo 'TURNSERVER_ENABLED=1' >> /etc/default/coturn

systemctl enable coturn
systemctl restart coturn

echo "==> TURN secret (add to home /opt/deep-messenger/.env as TURN_SECRET):"
echo "$SECRET"
echo "coturn OK — open UDP/TCP 3478 on VPS firewall if needed"
