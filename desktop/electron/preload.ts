import { contextBridge } from 'electron';

contextBridge.exposeInMainWorld('deepDesktop', {
  platform: process.platform
});
