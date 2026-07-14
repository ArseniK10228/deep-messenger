# Telegram Gateway — вход по номеру, код в Telegram

Официальный сервис: https://gateway.telegram.org

Код приходит **в Telegram** (не SMS). Номер в приложении = номер аккаунта Telegram.

## 1. Регистрация (5 мин)

1. Открой https://gateway.telegram.org
2. Войди через Telegram
3. **Account** → **API Token** → **Copy Token**

## 2. Сервер

В `/opt/deep-messenger/.env` на доме:

```
TELEGRAM_GATEWAY_TOKEN=ваш_токен_из_gateway
```

GitHub secret (для deploy): `TELEGRAM_GATEWAY_TOKEN`

Перезапуск: deploy workflow или `systemctl restart deep-messenger`

## 3. Тест бесплатно

Отправка кода **на свой номер** — **бесплатно** (для разработки).

Чужим номерам — ~$0.01 за код (дешевле SMS).

## 4. Требования к пользователю

- Установлен **Telegram**
- Номер телефона **привязан** к Telegram (Settings → Phone Number)
- В Deep вводит **тот же** номер в формате +7...

## 5. API

- `POST /api/v1/auth/telegram/send` — `{ "phone": "+7900..." }` → `{ requestId }`
- `POST /api/v1/auth/telegram/verify` — `{ "requestId", "code" }` → JWT

## 6. Android

Пересобери APK после обновления. Экран логина: «Получить код в Telegram».

Firebase Phone Auth больше не используется (FCM push — по-прежнему Firebase).
