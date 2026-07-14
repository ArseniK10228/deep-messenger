import { query } from '../db/client.js';

export interface UserRow {
  id: string;
  phone: string;
  display_name: string;
  avatar_path: string | null;
  fcm_token: string | null;
}

export async function findUserByPhone(phone: string): Promise<UserRow | null> {
  const r = await query<UserRow>('SELECT * FROM users WHERE phone = $1', [phone]);
  return r.rows[0] || null;
}

export async function upsertUserByPhone(phone: string, displayName?: string): Promise<UserRow> {
  const existing = await findUserByPhone(phone);
  if (existing) {
    if (displayName && displayName !== existing.display_name) {
      await query('UPDATE users SET display_name = $2, updated_at = now() WHERE id = $1', [
        existing.id,
        displayName
      ]);
      return { ...existing, display_name: displayName };
    }
    return existing;
  }
  const r = await query<UserRow>(
    `INSERT INTO users (phone, display_name) VALUES ($1, $2) RETURNING *`,
    [phone, displayName || phone]
  );
  return r.rows[0];
}

export async function setFcmToken(userId: string, token: string): Promise<void> {
  await query('UPDATE users SET fcm_token = $2, updated_at = now() WHERE id = $1', [userId, token]);
}

export async function getUserById(id: string): Promise<UserRow | null> {
  const r = await query<UserRow>('SELECT * FROM users WHERE id = $1', [id]);
  return r.rows[0] || null;
}

export async function searchUsersByPhonePrefix(phonePrefix: string, excludeUserId: string): Promise<UserRow[]> {
  const r = await query<UserRow>(
    `SELECT id, phone, display_name, avatar_path, fcm_token
     FROM users
     WHERE phone LIKE $1 AND id <> $2
     ORDER BY phone
     LIMIT 20`,
    [`${phonePrefix}%`, excludeUserId]
  );
  return r.rows;
}
