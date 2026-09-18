# Deep Messenger

Личный мессенджер **Deep** — Android APK, **Windows desktop** (Electron), сервер на домашней машине.

**Главная памятка:** [docs/PROJECT-STATUS.md](docs/PROJECT-STATUS.md) — порты, DNS, что сделано, что дальше.

## Быстрый старт

```bash
cp .env.example .env
docker compose up -d
cd server && npm install && npm run migrate && npm run dev
```

## Деплой (прод)

1. GitHub repo + secrets (`VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`, `FIREBASE_SERVICE_ACCOUNT_JSON`)
2. Push `main` или Run **Deploy Deep Messenger** в Actions

## Desktop (Windows)

```bash
cd desktop && npm install && npm run dev    # разработка
cd desktop && npm run dist                  # установщик .exe в desktop/release/
```

См. [desktop/README.md](desktop/README.md). CI: workflow **Build Desktop** → artifact с установщиком.

## URL

- `https://api.deepdesignpc.online` — API
- `https://deepdesignpc.online` — лендинг / APK

## Документация

- [PROJECT-STATUS.md](docs/PROJECT-STATUS.md) — полный статус
- [INFRA.md](docs/INFRA.md) — сеть и порты
- [FIREBASE-SETUP.md](docs/FIREBASE-SETUP.md) — SMS и push
