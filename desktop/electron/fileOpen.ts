import { app, ipcMain, shell } from 'electron';
import fs from 'node:fs';
import path from 'node:path';

function sanitizeFileName(name: string): string {
  const base = path.basename(name).replace(/[^\w.\- ()[\]а-яА-ЯёЁ]+/gu, '_');
  return base.length > 0 ? base.slice(0, 180) : 'file';
}

export function registerFileOpenHandlers(): void {
  ipcMain.handle(
    'file:open',
    async (
      _event,
      payload: { url: string; fileName: string; authToken?: string | null }
    ) => {
      const { url, fileName, authToken } = payload;
      if (!url?.startsWith('http')) {
        throw new Error('invalid url');
      }

      const dir = path.join(app.getPath('temp'), 'deep-chat-files');
      fs.mkdirSync(dir, { recursive: true });
      const safe = sanitizeFileName(fileName);
      const dest = path.join(dir, `${Date.now()}-${safe}`);

      const headers: Record<string, string> = {};
      if (authToken) headers.Authorization = `Bearer ${authToken}`;

      const res = await fetch(url, { headers });
      if (!res.ok) {
        throw new Error(`download failed (${res.status})`);
      }
      const buf = Buffer.from(await res.arrayBuffer());
      fs.writeFileSync(dest, buf);

      const openErr = await shell.openPath(dest);
      if (openErr) {
        throw new Error(openErr);
      }
      return { path: dest };
    }
  );
}
