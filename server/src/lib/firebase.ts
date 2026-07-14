import admin from 'firebase-admin';
import fs from 'fs';
import { config } from '../config.js';

let initialized = false;

export function initFirebase(): void {
  if (initialized) return;
  const p = config.firebaseServiceAccountPath;
  if (!p || !fs.existsSync(p)) {
    console.warn('[firebase] service account not configured — auth disabled until FIREBASE_SERVICE_ACCOUNT_PATH is set');
    return;
  }
  const cred = JSON.parse(fs.readFileSync(p, 'utf8'));
  admin.initializeApp({ credential: admin.credential.cert(cred) });
  initialized = true;
}

export async function verifyFirebaseIdToken(idToken: string): Promise<{ phone: string; uid: string }> {
  if (!admin.apps.length) {
    throw new Error('Firebase not configured');
  }
  const decoded = await admin.auth().verifyIdToken(idToken);
  const phone = decoded.phone_number;
  if (!phone) {
    throw new Error('Phone number missing in Firebase token');
  }
  return { phone, uid: decoded.uid };
}

export async function sendPush(fcmToken: string, title: string, body: string, data?: Record<string, string>): Promise<void> {
  if (!admin.apps.length || !fcmToken) return;
  await admin.messaging().send({
    token: fcmToken,
    notification: { title, body },
    data: data || {},
    android: { priority: 'high' }
  });
}

export async function sendCallPush(
  calleeUserId: string,
  data: Record<string, string>
): Promise<void> {
  if (!admin.apps.length) return;
  const { query } = await import('../db/client.js');
  const r = await query<{ fcm_token: string | null }>(
    'SELECT fcm_token FROM users WHERE id = $1',
    [calleeUserId]
  );
  const token = r.rows[0]?.fcm_token;
  if (!token) return;

  await admin.messaging().send({
    token,
    data,
    android: {
      priority: 'high',
      ttl: 30_000
    }
  });
}
