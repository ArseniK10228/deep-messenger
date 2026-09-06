# Инфраструктура Deep Messenger

**Полная памятка:** [PROJECT-STATUS.md](PROJECT-STATUS.md)

## Схема (без новых пробросов роутера)

```
APK → api.deepdesignpc.online:443 (VPS)
         ↓ Tailscale
      дом :3002 (Deep API)
```

| Порт роутера | Занят | Deep |
|--------------|-------|------|
| 8443 | бот | не трогаем |
| 7777 | сайт | не трогаем |
| 3002 | — | только Tailscale, не снаружи |

## DNS

| Запись | A |
|--------|---|
| `@` | `2.56.120.54` |
| `api` | `2.56.120.54` |
| `turn` | `2.56.120.54` (coturn, UDP/TCP 3478 на VPS) |

## WebRTC / TURN

- **Signaling** — тот же `wss://api.deepdesignpc.online/ws`
- **Media relay** — coturn на **VPS** (не на доме, без проброса UDP на роутер)
- Deploy: `scripts/infra/apply-vps-turn.sh`
- Секрет: `/etc/deep-messenger-turn-secret` на VPS → `TURN_SECRET` в home `.env`

## Деплой

См. [GITHUB-DEPLOY.md](GITHUB-DEPLOY.md)
