import dotenv from 'dotenv';
import path from 'path';
import { fileURLToPath } from 'url';

const serverRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = path.resolve(serverRoot, '..');

dotenv.config({ path: path.join(repoRoot, '.env') });
dotenv.config({ path: path.join(serverRoot, '.env') });

export const config = {
  nodeEnv: process.env.NODE_ENV || 'development',
  port: Number(process.env.PORT || 3002),
  host: process.env.HOST || '0.0.0.0',
  jwtSecret: process.env.JWT_SECRET || 'dev-secret-change-me',
  publicUrl: (process.env.PUBLIC_URL || 'http://localhost:3002').replace(/\/$/, ''),
  databaseUrl: process.env.DATABASE_URL || 'postgres://deep:deep@127.0.0.1:5432/deep_messenger',
  firebaseServiceAccountPath: process.env.FIREBASE_SERVICE_ACCOUNT_PATH || '',
  uploadDir: process.env.UPLOAD_DIR || './data/uploads',
  maxUploadMb: Number(process.env.MAX_UPLOAD_MB || 50),
  deleteForEveryoneHours: 48
};
