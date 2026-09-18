# Deep Messenger — Desktop (Windows)

Electron-клиент с тем же API, что и Android: email-вход, чаты, текст/фото/голос/файлы/видеокружки, realtime через WebSocket.

## Разработка

```bash
cd desktop
npm install
npm run dev
```

## Сборка установщика (.exe)

```bash
cd desktop
npm install
npm run dist
```

Артефакты в `desktop/release/`:

- NSIS-установщик `Deep Messenger Setup x.x.x.exe`
- или portable: `npm run dist:portable`

## API

По умолчанию `https://api.deepdesignpc.online`. Переопределение:

```bash
set VITE_API_BASE_URL=https://api.deepdesignpc.online
npm run build
```

## Ограничения v1

- Звонки WebRTC — только в мобильном приложении
- Запись видеокружков с камеры — в мобильном (на ПК можно смотреть и отправлять файлы)
