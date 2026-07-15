import { query } from '../db/client.js';

export interface UserRow {
  id: string;
  phone: string | null;
  email: string | null;
  display_name: string;
  avatar_path: string | null;
  fcm_token: string | null;
}

export async function findUserByEmail(email: string): Promise<UserRow | null> {
  const r = await query<UserRow>('SELECT * FROM users WHERE email = $1', [email]);
  return r.rows[0] || null;
}

export async function upsertUserByEmail(email: string, displayName?: string): Promise<UserRow> {
  const existing = await findUserByEmail(email);
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
  const local = email.split('@')[0] || email;
  const r = await query<UserRow>(
    `INSERT INTO users (email, display_name) VALUES ($1, $2) RETURNING *`,
    [email, displayName || local]
  );
  return r.rows[0];
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

export async function searchUsersByQuery(
  q: string,
  excludeUserId: string
): Promise<UserRow[]> {
  const term = q.trim().toLowerCase();
  const r = await query<UserRow>(
    `SELECT id, phone, email, display_name, avatar_path, fcm_token
     FROM users
     WHERE id <> $2
       AND (
         (email IS NOT NULL AND email ILIKE $1)
         OR (phone <> '' AND phone LIKE $3)
         OR display_name ILIKE $1
       )
     ORDER BY display_name, email, phone
     LIMIT 20`,
    [`%${term}%`, excludeUserId, `${q.replace(/\D/g, '')}%`]
  );
  return r.rows;
}

export async function searchUsersByPhonePrefix(phonePrefix: string, excludeUserId: string): Promise<UserRow[]> {
  const r = await query<UserRow>(
    `SELECT id, phone, email, display_name, avatar_path, fcm_token
     FROM users
     WHERE phone LIKE $1 AND id <> $2 AND phone <> ''
     ORDER BY phone
     LIMIT 20`,
    [`${phonePrefix}%`, excludeUserId]
  );
  return r.rows;
}
