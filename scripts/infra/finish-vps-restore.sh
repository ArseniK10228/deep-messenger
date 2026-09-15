#!/usr/bin/env bash
# Run on VPS after DNS @, api, turn → this server's public IP.
set -euo pipefail

REPO="${REPO:-/opt/deep-messenger}"
if [[ ! -d "$REPO/infra" && -d /tmp/deep-messenger/infra ]]; then
  REPO=/tmp/deep-messenger
fi

PUBLIC_IP="${PUBLIC_IP:-$(curl -fsS --max-time 8 https://api.ipify.org)}"
echo "==> Public IP: $PUBLIC_IP"

check_dns() {
  local host="$1"
  local ip
  ip=$(getent ahostsv4 "$host" 2>/dev/null | awk '{print $1}' | head -1 || true)
  [[ "$ip" == "$PUBLIC_IP" ]]
}

ok=1
for h in deepdesignpc.online api.deepdesignpc.online turn.deepdesignpc.online; do
  if check_dns "$h"; then
    echo "DNS OK: $h → $PUBLIC_IP"
  else
    echo "DNS WAIT: $h does not resolve to $PUBLIC_IP yet"
    ok=0
  fi
done

if [[ "$ok" -ne 1 ]]; then
  echo "Update A records at reg.ru, then re-run this script."
  exit 1
fi

bash "$REPO/scripts/infra/apply-vps.sh"
echo "==> smoke"
curl -fsS https://api.deepdesignpc.online/health
curl -fsSI https://deepdesignpc.online/deep.apk | head -5
echo "finish-vps-restore OK"
