# Deep Android — этапы разработки

## Этап 1 ✅ (текущий)
- Gradle + Jetpack Compose
- Тема Deep (`#9D5CFF`, `#0D0D0F`)
- Навигация: Splash → Login / Chats
- API client + SessionStore (DataStore)
- Анимации `deepAppear` (GPU `graphicsLayer`, 120Hz-friendly)

## Этап 2 ✅
- Firebase Phone Auth
- Экран телефона + SMS-код
- Обмен idToken → JWT с сервером

## Этап 3 ✅
- Список диалогов (`ChatsScreen`)
- Экран переписки + WebSocket (`ChatScreen`, `ChatSocket`)
- Поиск контакта по номеру → direct chat
- Пузыри входящих/исходящих

## Этап 4 — Медиа
- Фото, файлы, голосовые сообщения
- Coil + upload API

## Этап 5 — Звонки
- WebRTC audio only
- Foreground service, низкая задержка
- TURN на сервере

## Сборка

1. Android Studio → Open `android/`
2. Sync Gradle
3. Firebase: добавить **SHA-1 debug** в Console:
   ```powershell
   keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android"
   ```
4. Run или `cd android && .\gradlew.bat assembleDebug`

APK: `android/app/build/outputs/apk/debug/app-debug.apk`
