import { isValidUsername, normalizeSearchKey, normalizeUsername } from '../lib/searchNormalize.js';
import { isOwnerUserId, isReservedUsername } from '../lib/operator.js';
import { isUserOnline } from '../ws/hub.js';
import { query } from './client.js';

export interface ClientState {
  foreground?: boolean;
  batteryPct?: number | null;
  charging?: boolean | null;
  network?: string | null;
  inCall?: boolean | null;
}

export interface UserRow {
  id: string;
  phone: string | null;
  email: string | null;
  username: string | null;
  display_name: string;
  avatar_path: string | null;
  fcm_token: string | null;
  app_version_code?: number | null;
  app_version_name?: string | null;
  app_version_at?: string | null;
  last_seen_at?: string | null;
  client_state?: ClientState | null;
  client_state_at?: string | null;
  created_at?: string | null;
}

export function mapUserDto(row: UserRow) {
  return {
    id: row.id,
    email: row.email,
    phone: row.phone || '',
    username: row.username,
    displayName: row.display_name,
    avatarUrl: row.avatar_path ? `/media/${row.avatar_path}` : null,
    canViewPresence: isOwnerUserId(row.id)
  };
}

export async function listUsersAdmin(): Promise<UserRow[]> {
  const r = await query<UserRow>(
    `SELECT * FROM users ORDER BY COALESCE(last_seen_at, created_at) DESC`
  );
  return r.rows;
}

export function mapAdminUserDto(row: UserRow) {
  const online = isUserOnline(row.id);
  return {
    ...mapPeerDto(row, true),
    online,
    lastSeenAt: online ? null : row.last_seen_at ?? null,
    clientState: row.client_state ?? null,
    clientStateAt: row.client_state_at ?? null,
    createdAt: row.created_at ?? null
  };
}

export function mapPeerDto(row: UserRow, viewerIsOperator: boolean) {
  const base = mapUserDto(row);
  if (!viewerIsOperator) {
    return base;
  }
  return {
    ...base,
    online: false,
    lastSeenAt: row.last_seen_at ?? null,
    appVersionCode: row.app_version_code ?? null,
    appVersionName: row.app_version_name ?? null,
    clientState: row.client_state ?? null,
    clientStateAt: row.client_state_at ?? null
  };
}

async function suggestUsernameFromEmail(email: string, excludeUserId?: string): Promise<string | null> {
  const local = (email.split('@')[0] || '').toLowerCase();
  if (!isValidUsername(local)) return null;
  const taken = await query(
    `SELECT 1 FROM users WHERE lower(username) = $1 ${excludeUserId ? 'AND id <> $2' : ''} LIMIT 1`,
    excludeUserId ? [local, excludeUserId] : [local]
  );
  return taken.rowCount ? null : local;
}

export async function findUserByEmail(email: string): Promise<UserRow | null> {
  const r = await query<UserRow>('SELECT * FROM users WHERE email = $1', [email]);
  return r.rows[0] || null;
}

export async function upsertUserByEmail(email: string, displayName?: string): Promise<UserRow> {
  const existing = await findUserByEmail(email);
  if (existing) {
    let row = existing;
    if (displayName && displayName !== existing.display_name) {
      await query('UPDATE users SET display_name = $2, updated_at = now() WHERE id = $1', [
        existing.id,
        displayName
      ]);
      row = { ...row, display_name: displayName };
    }
    if (!row.username) {
      const username = await suggestUsernameFromEmail(email, row.id);
      if (username) {
        await query('UPDATE users SET username = $2, updated_at = now() WHERE id = $1', [
          row.id,
          username
        ]);
        row = { ...row, username };
      }
    }
    return row;
  }

  const local = email.split('@')[0] || email;
  const username = await suggestUsernameFromEmail(email);
  const r = await query<UserRow>(
    `INSERT INTO users (email, display_name, username) VALUES ($1, $2, $3) RETURNING *`,
    [email, displayName || local, username]
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

export async function setFcmToken(
  userId: string,
  token: string,
  versionCode?: number | null,
  versionName?: string | null
): Promise<void> {
  if (versionCode && versionName) {
    await query(
      `UPDATE users SET
         fcm_token = $2,
         app_version_code = $3,
         app_version_name = $4,
         app_version_at = now(),
         updated_at = now()
       WHERE id = $1`,
      [userId, token, versionCode, versionName.trim()]
    );
    return;
  }
  await query('UPDATE users SET fcm_token = $2, updated_at = now() WHERE id = $1', [userId, token]);
}

export async function setClientVersion(
  userId: string,
  versionCode: number,
  versionName: string,
  clientState?: ClientState | null
): Promise<void> {
  if (clientState) {
    await query(
      `UPDATE users SET
         app_version_code = $2,
         app_version_name = $3,
         app_version_at = now(),
         client_state = $4::jsonb,
         client_state_at = now(),
         updated_at = now()
       WHERE id = $1`,
      [userId, versionCode, versionName.trim(), JSON.stringify(clientState)]
    );
    return;
  }
  await query(
    `UPDATE users SET
       app_version_code = $2,
       app_version_name = $3,
       app_version_at = now(),
       updated_at = now()
     WHERE id = $1`,
    [userId, versionCode, versionName.trim()]
  );
}

export async function getUserById(id: string): Promise<UserRow | null> {
  const r = await query<UserRow>('SELECT * FROM users WHERE id = $1', [id]);
  return r.rows[0] || null;
}

export async function updateUserProfile(
  userId: string,
  input: { displayName?: string; username?: string }
): Promise<UserRow> {
  const current = await getUserById(userId);
  if (!current) throw new Error('USER_NOT_FOUND');

  const displayName = input.displayName?.trim();
  const usernameRaw = input.username !== undefined ? normalizeUsername(input.username) : undefined;

  if (usernameRaw !== undefined) {
    if (usernameRaw && !isValidUsername(usernameRaw)) {
      throw new Error('INVALID_USERNAME');
    }
    if (usernameRaw && isReservedUsername(usernameRaw) && userId !== current.id) {
      throw new Error('USERNAME_TAKEN');
    }
    if (usernameRaw) {
      const taken = await query(
        'SELECT 1 FROM users WHERE lower(username) = $1 AND id <> $2 LIMIT 1',
        [usernameRaw, userId]
      );
      if (taken.rowCount) throw new Error('USERNAME_TAKEN');
    }
  }

  const r = await query<UserRow>(
    `UPDATE users SET
       display_name = COALESCE($2, display_name),
       username = CASE WHEN $3::text IS NULL THEN username ELSE NULLIF($3, '') END,
       updated_at = now()
     WHERE id = $1
     RETURNING *`,
    [userId, displayName || null, usernameRaw ?? null]
  );
  return r.rows[0];
}

function userMatchesQuery(row: UserRow, rawQuery: string): boolean {
  const key = normalizeSearchKey(rawQuery);
  if (!key) return false;

  const haystacks = [
    row.username,
    row.display_name,
    row.email,
    row.email?.split('@')[0],
    row.phone
  ];

  return haystacks.some((part) => {
    if (!part) return false;
    const normalized = normalizeSearchKey(part);
    return normalized.includes(key) || part.toLowerCase().includes(rawQuery.trim().toLowerCase());
  });
}

export async function searchUsersByQuery(q: string, excludeUserId: string): Promise<UserRow[]> {
  const raw = q.trim().replace(/^@/, '');
  if (raw.length < 2) return [];

  const loose = `%${raw.toLowerCase()}%`;
  const phonePrefix = `${raw.replace(/\D/g, '')}%`;

  const r = await query<UserRow>(
    `SELECT id, phone, email, username, display_name, avatar_path, fcm_token
     FROM users
     WHERE id <> $1
       AND (
         (email IS NOT NULL AND email ILIKE $2)
         OR split_part(email, '@', 1) ILIKE $2
         OR (username IS NOT NULL AND username ILIKE $2)
         OR display_name ILIKE $2
         OR (phone IS NOT NULL AND phone <> '' AND phone LIKE $3)
       )
     ORDER BY
       CASE
         WHEN username IS NOT NULL AND lower(username) = lower($4) THEN 0
         WHEN username IS NOT NULL AND username ILIKE $5 THEN 1
         ELSE 2
       END,
       display_name,
       username,
       email
     LIMIT 50`,
    [excludeUserId, loose, phonePrefix, raw.toLowerCase(), `${raw.toLowerCase()}%`]
  );

  return r.rows.filter((row) => userMatchesQuery(row, raw)).slice(0, 20);
}

export async function searchUsersByPhonePrefix(
  phonePrefix: string,
  excludeUserId: string
): Promise<UserRow[]> {
  const r = await query<UserRow>(
    `SELECT id, phone, email, username, display_name, avatar_path, fcm_token
     FROM users
     WHERE phone LIKE $1 AND id <> $2 AND phone <> ''
     ORDER BY phone
     LIMIT 20`,
    [`${phonePrefix}%`, excludeUserId]
  );
  return r.rows;
}
