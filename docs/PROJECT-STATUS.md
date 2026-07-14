# Deep Messenger — статус проекта (памятка для агента)

> Обновлять при каждом значимом шаге. Последнее обновление: 2026-07-14.

## Цель

Личный мессенджер **Deep** на двоих: Android APK, сервер на **домашней машине**, публичный вход без VPN.

| Фича | Статус |
|------|--------|
| Текст, фото, файлы, голосовые | API готов, UI — нет |
| Регистрация по телефону (Firebase SMS) | Firebase настроен, Android — нет |
| Push (FCM) | Сервер готов |
| Удаление как в ТГ | API готов |
| Голосовые звонки | Этап 2 (WebRTC + TURN) |

---

## Инфраструктура (факты, проверено)

### Роутер — проброшены ТОЛЬКО эти порты

| Внешний | Внутренний | Сервис | Трогать? |
|---------|------------|--------|----------|
| **8443** | дом:8443 | Mini App / бот (`:3000`) | **НЕТ** |
| **7777** | дом:80 | Статика сайта `deepdesignpc.ru` | **НЕТ** |
| 80, 443 | папин сервер | Не наш | — |

**Новые пробросы невозможны** — Deep на них не лезет.

### Deep — без новых пробросов

```
Android APK
    ↓ HTTPS :443
VPS 138.124.102.53
    api.deepdesignpc.online  (SSL здесь)
    ↓ Tailscale (НЕ через роутер)
Дом 100.118.211.24:3002
    Node Deep Messenger + PostgreSQL
```

Аналогично **staging** (`VPS :443 → Tailscale :3001`).

### DNS (готово)

| Запись | Значение |
|--------|----------|
| `deepdesignpc.online` | A → `138.124.102.53` |
| `api.deepdesignpc.online` | A → `138.124.102.53` |

`www.deepdesignpc.online` — **не создан** (certbot без www).

### SSL (готово на VPS)

- `/etc/letsencrypt/live/deepdesignpc.online/`
- nginx: `/etc/nginx/sites-enabled/deep-online.conf`

### Порты на доме (Tailscale, с VPS)

| Порт | Сервис | Статус |
|------|--------|--------|
| 3000 | deepdesign-bot prod | ✅ |
| 3001 | staging | ✅ |
| **3002** | **Deep Messenger** | ❌ не поднят |
| 8080 | статика для VPS | ✅ |
| 8443 | nginx → :3000 (снаружи) | ✅ |
| 22 SSH | root | ⚠️ висит с VPS (ключ?) |

### Публичные проверки

```bash
curl -fsS https://app.deepdesignpc.ru:8443/health      # 200
curl -fsS https://77.239.227.66:7777/                   # 200 (сайт)
curl -fsS https://deepdesignpc.online/                  # 200 (лендинг VPS)
curl -fsS https://api.deepdesignpc.online/health         # 502 пока нет :3002
```

---

## Репозиторий

| Путь | Описание |
|------|----------|
| `C:\Users\Arsen\Project\deep-messenger` | Локальный git (отдельно от DeepDesignProject) |
| `/opt/deep-messenger` | Путь на домашнем сервере (после деплоя) |

### Секреты (НЕ в git)

| Файл | Назначение |
|------|------------|
| `android/app/google-services.json` | Firebase Android |
| `secrets/firebase-service-account.json` | Firebase Admin (сервер) |
| `.env` на доме | JWT_SECRET, DATABASE_URL |

В GitHub Actions: secret `FIREBASE_SERVICE_ACCOUNT_JSON` (весь JSON).

---

## Firebase

- Проект: **Deep Messenger** (`deep-messenger-ru`)
- Package: `online.deepdesign.deep`
- Phone Auth: **включён**
- План: Spark (для прод SMS может понадобиться Blaze)

---

## Дизайн Deep

| Токен | HEX |
|-------|-----|
| background | `#0D0D0F` |
| surface | `#1A1A1E` |
| accent | `#9D5CFF` |
| text | `#FFFFFF` |
| muted | `#8E8E93` |
| error | `#FF5E5E` |

---

## Деплой

### Автоматический (основной путь)

1. Создать GitHub repo `deep-messenger`
2. Secrets (как у DeepDesign): `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`, `FIREBASE_SERVICE_ACCOUNT_JSON`
3. Push `main` или **Run workflow** → `.github/workflows/deploy.yml`

Цепочка: `GitHub Actions → VPS root → home root (home_deploy) → /opt/deep-messenger`

### Ручной (если есть Tailscale на ПК)

```bash
ssh visuals-ts
sudo bash /opt/deep-messenger/scripts/infra/apply-home.sh
```

### Почему агент «висит»

- SSH VPS→дом иногда 50+ сек (не перезагружать сервер из-за этого)
- Длинные `apt install` / `npm ci` через MCP — таймаут
- С ПК `ssh visuals-ts` без Tailscale = таймаут (порт 22 не проброшен)

---

## Что сделано

- [x] Сервер API (Fastify, WS, PostgreSQL schema)
- [x] DNS deepdesignpc.online
- [x] SSL + nginx на VPS
- [x] Firebase + конфиги локально
- [x] Deploy workflow (файлы в репо)
- [x] Первый деплой на дом `:3002` (native PostgreSQL :5432, commit caf5509)
- [x] `https://api.deepdesignpc.online/health` → ok
- [ ] Android APK (Compose UI)
- [ ] Голосовые звонки

---

## Следующие шаги (порядок)

1. **GitHub repo** + secrets → Run deploy workflow
2. Проверить `curl https://api.deepdesignpc.online/health` → `{"ok":true,...}`
3. Android: login по SMS + чат
4. Залить APK на `deepdesignpc.online/deep.apk`
5. Этап 2: coturn для звонков (понадобятся UDP пробросы — обсудить отдельно)

---

## API (кратко)

- `POST /api/v1/auth/firebase` — `{ idToken }` → JWT
- `POST /api/v1/auth/fcm` — push token
- `GET /api/v1/conversations` — список чатов
- `POST /api/v1/conversations/direct` — `{ userId }`
- `GET/POST /api/v1/conversations/:id/messages`
- `POST /api/v1/conversations/:id/upload` — медиа
- `WS /ws?token=JWT` — realtime

---

## Связанные файлы

| Файл | Роль |
|------|------|
| `infra/nginx-vps-deep-online.conf` | VPS nginx |
| `infra/deep-messenger.service` | systemd на доме |
| `scripts/infra/apply-home.sh` | Деплой на дом |
| `scripts/infra/apply-vps.sh` | nginx на VPS |
| `.github/workflows/deploy.yml` | CI/CD |
| `docs/FIREBASE-SETUP.md` | Firebase пошагово |
| `docs/INFRA.md` | Инфра кратко |
