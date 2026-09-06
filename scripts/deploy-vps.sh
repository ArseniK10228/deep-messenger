#!/usr/bin/env bash
# Запускать на VPS (ssh root@2.56.120.54)
set -euo pipefail

CONF_SRC="${1:-/tmp/nginx-vps-deep-online.conf}"

echo "==> certbot (если сертификата ещё нет)"
if [[ ! -f /etc/letsencrypt/live/deepdesignpc.online/fullchain.pem ]]; then
  certbot certonly --nginx -d deepdesignpc.online -d www.deepdesignpc.online -d api.deepdesignpc.online --non-interactive --agree-tos -m admin@deepdesignpc.online || {
    echo "certbot failed — проверь DNS и что домен смотрит на этот VPS"
    exit 1
  }
fi

echo "==> nginx"
install -D -m 644 "$CONF_SRC" /etc/nginx/sites-available/deep-online.conf
ln -sf /etc/nginx/sites-available/deep-online.conf /etc/nginx/sites-enabled/deep-online.conf
nginx -t
systemctl reload nginx

echo "==> landing"
mkdir -p /var/www/deep-messenger
if [[ -f /tmp/deep-landing-index.html ]]; then
  cp /tmp/deep-landing-index.html /var/www/deep-messenger/index.html
fi

echo "Done. Check: curl -fsS https://api.deepdesignpc.online/health"
