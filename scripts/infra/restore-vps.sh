#!/usr/bin/env bash
# Full VPS restore after rebuild. Run as root on the VPS (2.56.120.54).
# Prerequisite: DNS A records for @, api, turn → this VPS public IP.
set -euo pipefail

REPO="${REPO:-/tmp/deep-messenger}"
PUBLIC_IP="${PUBLIC_IP:-$(curl -fsS --max-time 5 https://api.ipify.org || true)}"

echo "==> Deep Messenger VPS restore (IP: ${PUBLIC_IP:-unknown})"

if [[ ! -f "$REPO/infra/nginx-vps-deep-online.conf" ]]; then
  echo "ERROR: deploy bundle missing at $REPO — upload via CI or scp first"
  exit 1
fi

# Tailscale → home API
if ! tailscale status 2>/dev/null | grep -q '100.118.211.24'; then
  echo "WARN: home server 100.118.211.24 not visible in tailscale status"
fi

if ! ssh -i /root/.ssh/home_deploy -o BatchMode=yes -o ConnectTimeout=15 root@100.118.211.24 'echo HOME_OK' 2>/dev/null; then
  echo "ERROR: VPS cannot SSH to home via /root/.ssh/home_deploy"
  exit 1
fi

mkdir -p /var/www/deep-messenger
if [[ -f "$REPO/infra/landing/index.html" ]]; then
  install -m 644 "$REPO/infra/landing/index.html" /var/www/deep-messenger/index.html
fi

if [[ ! -f /etc/letsencrypt/live/deepdesignpc.online/fullchain.pem ]]; then
  echo "==> certbot (needs DNS @, api → this VPS)"
  certbot certonly --nginx \
    -d deepdesignpc.online \
    -d api.deepdesignpc.online \
    --non-interactive --agree-tos --register-unsafely-without-email
fi

bash "$REPO/scripts/infra/apply-vps.sh"
bash "$REPO/scripts/infra/apply-vps-turn.sh"
bash "$REPO/scripts/infra/sync-turn-secret.sh"

echo "==> smoke (local)"
curl -fsS -H 'Host: api.deepdesignpc.online' http://127.0.0.1/health || true
echo ""
echo "Restore done. After DNS propagates:"
echo "  curl -fsS https://api.deepdesignpc.online/health"
