import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import type { FastifyInstance } from 'fastify';

const routesDir = path.dirname(fileURLToPath(import.meta.url));
// dist/routes/app.js → server/app-release.json
const releasePath = path.resolve(routesDir, '../../app-release.json');

export interface AppRelease {
  versionCode: number;
  versionName: string;
  apkUrl: string;
  changelog?: string;
  forceUpdate?: boolean;
}

function readRelease(): AppRelease {
  const raw = fs.readFileSync(releasePath, 'utf8');
  const data = JSON.parse(raw) as Partial<AppRelease>;
  if (!data.versionCode || !data.versionName?.trim()) {
    throw new Error('invalid app-release.json: versionCode/versionName required');
  }
  return {
    versionCode: data.versionCode,
    versionName: data.versionName.trim(),
    apkUrl: data.apkUrl || 'https://deepdesignpc.online/deep.apk',
    changelog: data.changelog,
    forceUpdate: data.forceUpdate
  };
}

export async function registerAppRoutes(app: FastifyInstance): Promise<void> {
  app.get('/app/release', async () => readRelease());
}
