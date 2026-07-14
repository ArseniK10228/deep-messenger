import 'dotenv/config';

export const config = {
  nodeEnv: process.env.NODE_ENV || 'development',
  port: Number(process.env.PORT || 3002),
  host: process.env.HOST || '0.0.0.0',
  jwtSecret: process.env.JWT_SECRET || 'dev-secret-change-me',
  publicUrl: (process.env.PUBLIC_URL || 'http://localhost:3002').replace(/\/$/, ''),
  databaseUrl: process.env.DATABASE_URL || 'postgres://deep:deep@localhost:5433/deep_messenger',
  firebaseServiceAccountPath: process.env.FIREBASE_SERVICE_ACCOUNT_PATH || '',
  uploadDir: process.env.UPLOAD_DIR || './data/uploads',
  maxUploadMb: Number(process.env.MAX_UPLOAD_MB || 50),
  deleteForEveryoneHours: 48
};
