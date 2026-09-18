import { app, BrowserWindow, shell } from 'electron';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { registerFileOpenHandlers } from './fileOpen';
import { setupAutoUpdater } from './updater';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

process.env.DIST = path.join(__dirname, '../dist');
process.env.VITE_PUBLIC = app.isPackaged
  ? process.env.DIST
  : path.join(__dirname, '../public');

let win: BrowserWindow | null = null;

function resolveAppIcon(): string {
  if (app.isPackaged) {
    const ico = path.join(process.resourcesPath, 'icon.ico');
    if (fs.existsSync(ico)) return ico;
    return path.join(process.resourcesPath, 'icon.png');
  }
  const ico = path.join(__dirname, '../build/icon.ico');
  const png = path.join(__dirname, '../build/icon.png');
  if (fs.existsSync(ico)) return ico;
  return png;
}

function createWindow() {
  const iconPath = resolveAppIcon();
  win = new BrowserWindow({
    width: 1180,
    height: 760,
    minWidth: 920,
    minHeight: 600,
    title: 'Deep Messenger',
    icon: iconPath,
    backgroundColor: '#0d0d0f',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.mjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('http')) {
      shell.openExternal(url);
      return { action: 'deny' };
    }
    return { action: 'allow' };
  });

  if (process.env.VITE_DEV_SERVER_URL) {
    win.loadURL(process.env.VITE_DEV_SERVER_URL);
    win.webContents.openDevTools({ mode: 'detach' });
  } else {
    win.loadFile(path.join(process.env.DIST!, 'index.html'));
  }
}

app.whenReady().then(() => {
  app.setAppUserModelId('online.deepdesign.deep.desktop');
  registerFileOpenHandlers();
  createWindow();
  setupAutoUpdater(() => win);
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
