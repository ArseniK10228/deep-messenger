#!/usr/bin/env bash
# Deep Messenger — nginx on VPS. Run as root on 2.56.120.54
set -euo pipefail

REPO="${REPO:-/tmp/deep-messenger}"
CONF_SSL="${REPO}/infra/nginx-vps-deep-online.conf"
CONF_HTTP="${REPO}/infra/nginx-vps-deep-online-http-bootstrap.conf"

if [[ ! -f "$CONF_SSL" ]]; then
  echo "ERROR: $CONF_SSL not found"
  exit 1
fi

USE_SSL=0
if [[ -f /etc/letsencrypt/live/deepdesignpc.online/fullchain.pem ]]; then
  USE_SSL=1
else
  echo "==> certbot"
  if certbot certonly --nginx \
    -d deepdesignpc.online \
    -d api.deepdesignpc.online \
    --non-interactive --agree-tos --register-unsafely-without-email; then
    USE_SSL=1
  else
    echo "WARN: certbot failed — DNS may still point to old VPS. Using HTTP bootstrap."
  fi
fi

echo "==> nginx"
if [[ "$USE_SSL" -eq 1 ]]; then
  install -m 644 "$CONF_SSL" /etc/nginx/sites-available/deep-online.conf
else
  if [[ ! -f "$CONF_HTTP" ]]; then
    echo "ERROR: $CONF_HTTP not found"
    exit 1
  fi
  install -m 644 "$CONF_HTTP" /etc/nginx/sites-available/deep-online.conf
fi
ln -sf /etc/nginx/sites-available/deep-online.conf /etc/nginx/sites-enabled/deep-online.conf

mkdir -p /var/www/deep-messenger
if [[ -f "$REPO/infra/landing/index.html" ]]; then
  install -m 644 "$REPO/infra/landing/index.html" /var/www/deep-messenger/index.html
fi

nginx -t
systemctl reload nginx
if [[ "$USE_SSL" -eq 1 ]]; then
  echo "VPS nginx OK (SSL)"
else
  echo "VPS nginx OK (HTTP bootstrap — update DNS, then re-run apply-vps.sh)"
fi
