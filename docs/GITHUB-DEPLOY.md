# Деплой на GitHub

Один раз настроить, потом только push.

## 1. Создать репозиторий

GitHub → New repository → `deep-messenger` (private рекомендуется)

```powershell
cd C:\Users\Arsen\Project\deep-messenger
git add -A
git commit -m "Initial Deep Messenger: server, deploy, docs"
git remote add origin https://github.com/YOUR_USER/deep-messenger.git
git push -u origin main
```

## 2. Secrets (Settings → Secrets → Actions)

Скопировать из репозитория **DeepDesignProject** (те же значения):

| Secret | Значение |
|--------|----------|
| `VPS_HOST` | `2.56.120.54` |
| `VPS_USER` | `root` |
| `VPS_SSH_KEY` | приватный ключ `github_actions_deploy` |

Добавить новый:

| Secret | Значение |
|--------|----------|
| `FIREBASE_SERVICE_ACCOUNT_JSON` | **весь** файл `secrets/firebase-service-account.json` (copy-paste) |

## 3. Запуск

GitHub → Actions → **Deploy Deep Messenger** → Run workflow

Или push в `main`.

## 4. Проверка

```bash
curl https://api.deepdesignpc.online/health
# {"ok":true,"service":"deep-messenger","version":"0.1.0"}
```

## Если deploy падает на HOME_OK

SSH root на доме не принимает `home_deploy` ключ с VPS. Фикс — см. DeepDesignProject `docs/SSH-ACCESS.md`.
