# Восстановление после удаления VPS

## Схема (как было)

```
Телефон → https://api.deepdesignpc.online (VPS 2.56.120.54, nginx + SSL)
              ↓ Tailscale
         дом 100.118.211.24:3002 (Node API + Postgres)
```

На VPS же: статика `deepdesignpc.online/deep.apk`, **coturn** (`turn.deepdesignpc.online:3478`).

## Что уже поднято (проверено)

| Компонент | Статус |
|-----------|--------|
| Дом `deep-messenger` systemd :3002 | ✅ |
| VPS → дом по Tailscale | ✅ |
| VPS nginx HTTP proxy (bootstrap) | ✅ |
| APK на VPS `/var/www/deep-messenger/deep.apk` | ✅ |
| coturn + `TURN_SECRET` на доме | ✅ |
| GitHub `VPS_HOST=2.56.120.54` + deploy key | ✅ |

## Блокер: DNS на REG.RU

Сейчас A-записи всё ещё смотрят на **старый** IP `138.124.102.53`.

В панели [reg.ru](https://www.reg.ru) → домен **deepdesignpc.online** → DNS:

| Имя | Тип | Значение |
|-----|-----|----------|
| `@` | A | `2.56.120.54` |
| `api` | A | `2.56.120.54` |
| `turn` | A | `2.56.120.54` |

TTL можно 300–600 сек. Подожди 5–30 минут после сохранения.

## После смены DNS (на VPS)

```bash
ssh root@2.56.120.54
REPO=/tmp/deep-messenger bash /opt/deep-messenger/scripts/infra/finish-vps-restore.sh
# или если репо только в /tmp после deploy:
REPO=/tmp/deep-messenger bash scripts/infra/finish-vps-restore.sh
```

Скрипт: проверит, что DNS указывает на этот сервер → certbot → nginx SSL.

Проверка:

```bash
curl -fsS https://api.deepdesignpc.online/health
curl -fsSI https://deepdesignpc.online/deep.apk | head
```

## CI

- **Deploy Deep Messenger** — код на дом через VPS → Tailscale.
- **Build Android APK** — APK на VPS, метаданные на API.
