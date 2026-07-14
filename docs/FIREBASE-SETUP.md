# Firebase — SMS-коды и Push (пошагово)

Deep использует **Firebase Phone Authentication**: SMS шлёт Google, не твой сервер.
Регистрация **без whitelist** — любой номер, на который приходит SMS.

## Шаг 1. Создать проект Firebase

1. Открой https://console.firebase.google.com
2. **Add project** → имя `Deep Messenger` → Google Analytics можно выключить
3. **Create project**

## Шаг 2. Добавить Android-приложение

1. В проекте: иконка **Android** → Add app
2. Package name: `online.deepdesign.deep`
3. Скачай `google-services.json` → положи в `android/app/google-services.json`
4. Register app → дальше можно Skip до конца

## Шаг 3. Включить Phone Auth

1. **Build** → **Authentication** → **Get started**
2. Вкладка **Sign-in method**
3. **Phone** → Enable → Save

### Тестовые номера (для разработки без SMS)

В том же разделе Phone → **Phone numbers for testing**:
- `+79001234567` → код `123456`
- Удобно пока не настроен биллинг

## Шаг 4. Service Account для сервера

1. ⚙ **Project settings** → вкладка **Service accounts**
2. **Generate new private key** → скачается JSON
3. Положи файл на сервер:
   ```
   /opt/deep-messenger/secrets/firebase-service-account.json
   ```
4. В `.env`:
   ```
   FIREBASE_SERVICE_ACCOUNT_PATH=./secrets/firebase-service-account.json
   ```

Сервер проверяет Firebase ID token после входа по SMS на телефоне.

## Шаг 5. Cloud Messaging (push)

1. **Build** → **Cloud Messaging**
2. Для Android 13+ FCM работает из коробки с `google-services.json`
3. В приложении после логина токен шлётся на `POST /api/v1/auth/fcm`

## Шаг 6. Биллинг (для продакшн SMS)

Бесплатная квота Phone Auth ограничена. Для реальных SMS на любые номера:

1. **Upgrade** (Blaze plan) — pay-as-you-go
2. Обычно хватает копейки для двоих пользователей

Если Firebase на конкретном номере не сработает — fallback **SMS.ru** (добавим позже).

## Как это работает в приложении

```
1. Юзер вводит +7...
2. Firebase SDK → SMS с кодом
3. Юзер вводит код → Firebase выдаёт idToken
4. APK → POST /api/v1/auth/firebase { idToken }
5. Сервер проверяет token через Firebase Admin
6. Сервер выдаёт свой JWT → дальше чат
```

## Что тебе сделать руками (15 мин)

- [ ] Создать Firebase проект
- [ ] Скачать `google-services.json` в android
- [ ] Скачать service account JSON на сервер
- [ ] (Опционально) Blaze plan для SMS вне тестовых номеров

После этого скажешь — подключим в APK и на сервере.
