#!/usr/bin/env bash
# Deep Messenger — nginx on VPS. Run as root on 2.56.120.54
set -euo pipefail

REPO="${REPO:-/tmp/deep-messenger}"
CONF_SRC="${REPO}/infra/nginx-vps-deep-online.conf"

if [[ ! -f "$CONF_SRC" ]]; then
  echo "ERROR: $CONF_SRC not found"
  exit 1
fi

if [[ ! -f /etc/letsencrypt/live/deepdesignpc.online/fullchain.pem ]]; then
  echo "==> certbot"
  certbot certonly --nginx \
    -d deepdesignpc.online \
    -d api.deepdesignpc.online \
    --non-interactive --agree-tos --register-unsafely-without-email
fi

echo "==> nginx"
install -m 644 "$CONF_SRC" /etc/nginx/sites-available/deep-online.conf
ln -sf /etc/nginx/sites-available/deep-online.conf /etc/nginx/sites-enabled/deep-online.conf

mkdir -p /var/www/deep-messenger
if [[ -f "$REPO/infra/landing/index.html" ]]; then
  install -m 644 "$REPO/infra/landing/index.html" /var/www/deep-messenger/index.html
fi

nginx -t
systemctl reload nginx
echo "VPS nginx OK"
