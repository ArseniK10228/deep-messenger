import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('deepDesktop', {
  platform: process.platform,
  openChatFile: (url: string, fileName: string, authToken?: string | null) =>
    ipcRenderer.invoke('file:open', { url, fileName, authToken }),
  checkForUpdates: () => ipcRenderer.invoke('update:check'),
  installUpdate: () => ipcRenderer.invoke('update:install'),
  onUpdateStatus: (listener: (payload: unknown) => void) => {
    const handler = (_event: Electron.IpcRendererEvent, payload: unknown) => listener(payload);
    ipcRenderer.on('update-status', handler);
    return () => {
      ipcRenderer.removeListener('update-status', handler);
    };
  }
});
