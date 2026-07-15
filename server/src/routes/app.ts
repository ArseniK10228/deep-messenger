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
  return JSON.parse(raw) as AppRelease;
}

export async function registerAppRoutes(app: FastifyInstance): Promise<void> {
  app.get('/app/release', async () => readRelease());
}
