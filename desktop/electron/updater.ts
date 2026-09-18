import { app, BrowserWindow, ipcMain } from 'electron';
import { autoUpdater } from 'electron-updater';

export type UpdateStatusPayload =
  | { status: 'checking' }
  | { status: 'available'; version: string }
  | { status: 'not-available' }
  | { status: 'progress'; percent: number }
  | { status: 'ready'; version: string }
  | { status: 'error'; message: string };

function sendUpdate(win: BrowserWindow | null, payload: UpdateStatusPayload) {
  win?.webContents.send('update-status', payload);
}

export function setupAutoUpdater(getWindow: () => BrowserWindow | null) {
  if (!app.isPackaged) {
    ipcMain.handle('update:check', async () => ({ ok: false, reason: 'dev' }));
    ipcMain.handle('update:install', async () => ({ ok: false }));
    return;
  }

  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;
  autoUpdater.allowDowngrade = false;

  autoUpdater.on('checking-for-update', () => {
    sendUpdate(getWindow(), { status: 'checking' });
  });

  autoUpdater.on('update-available', (info) => {
    sendUpdate(getWindow(), { status: 'available', version: info.version });
  });

  autoUpdater.on('update-not-available', () => {
    sendUpdate(getWindow(), { status: 'not-available' });
  });

  autoUpdater.on('download-progress', (p) => {
    sendUpdate(getWindow(), { status: 'progress', percent: p.percent });
  });

  autoUpdater.on('update-downloaded', (info) => {
    sendUpdate(getWindow(), { status: 'ready', version: info.version });
  });

  autoUpdater.on('error', (err) => {
    sendUpdate(getWindow(), {
      status: 'error',
      message: err.message || 'Не удалось проверить обновления'
    });
  });

  ipcMain.handle('update:check', async () => {
    try {
      await autoUpdater.checkForUpdates();
      return { ok: true };
    } catch (e) {
      const message = e instanceof Error ? e.message : 'check failed';
      sendUpdate(getWindow(), { status: 'error', message });
      return { ok: false, message };
    }
  });

  ipcMain.handle('update:install', () => {
    autoUpdater.quitAndInstall(false, true);
  });

  const runCheck = () => {
    autoUpdater.checkForUpdates().catch(() => {});
  };

  setTimeout(runCheck, 6_000);
  setInterval(runCheck, 4 * 60 * 60 * 1000);
}
