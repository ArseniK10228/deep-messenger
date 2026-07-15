import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import type { FastifyInstance } from 'fastify';

const serverRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const releasePath = path.join(serverRoot, 'app-release.json');

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
