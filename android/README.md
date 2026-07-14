# Deep — Android

Пакет: `online.deepdesign.deep`  
API: `https://api.deepdesignpc.online`

## Требования

- Android Studio Ladybug+ 
- JDK 17
- `google-services.json` из [FIREBASE-SETUP.md](../docs/FIREBASE-SETUP.md)

## Сборка APK

```bash
cd android
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

## Дизайн Deep

| Токен | Значение |
|-------|----------|
| background | `#0D0D0F` |
| surface | `#1A1A1E` |
| accent | `#9D5CFF` |
| text | `#FFFFFF` |
| muted | `#8E8E93` |
| error | `#FF5E5E` |

## Статус

Скелет проекта. Следующий шаг — полный Compose UI:
- экран ввода телефона + SMS-код (Firebase)
- список чатов
- экран переписки (текст, фото, файлы, голосовые)
- push через FCM

Gradle-файлы будут добавлены при инициализации Android Studio project wizard.
