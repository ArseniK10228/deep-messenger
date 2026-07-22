import { getUserById } from '../db/users.js';
import { query } from '../db/client.js';
import { isOperatorUser } from './operator.js';
import { sendToUser } from '../ws/hub.js';
import { resolvePresenceOnline } from './presenceState.js';

export async function touchLastSeen(userId: string): Promise<string> {
  const r = await query<{ last_seen_at: string }>(
    `UPDATE users SET last_seen_at = now() WHERE id = $1 RETURNING last_seen_at`,
    [userId]
  );
  return r.rows[0]?.last_seen_at || new Date().toISOString();
}

export async function getPeerUserIds(userId: string): Promise<string[]> {
  const r = await query<{ user_id: string }>(
    `SELECT DISTINCT cm2.user_id
     FROM conversation_members cm1
     JOIN conversation_members cm2 ON cm2.conversation_id = cm1.conversation_id
     WHERE cm1.user_id = $1 AND cm2.user_id <> $1`,
    [userId]
  );
  return r.rows.map((row) => row.user_id);
}

export async function getLastSeenAt(userId: string): Promise<string | null> {
  const r = await query<{ last_seen_at: string | null }>(
    'SELECT last_seen_at FROM users WHERE id = $1',
    [userId]
  );
  return r.rows[0]?.last_seen_at || null;
}

export async function buildPresenceSnapshot(userId: string): Promise<
  Array<{ userId: string; online: boolean; lastSeenAt: string | null }>
> {
  if (!(await isOperatorUser(userId))) {
    return [];
  }
  const peers = await getPeerUserIds(userId);
  const snapshot: Array<{ userId: string; online: boolean; lastSeenAt: string | null }> = [];
  for (const peerId of peers) {
    const row = await getUserById(peerId);
    const online = row ? resolvePresenceOnline(row) : false;
    snapshot.push({
      userId: peerId,
      online,
      lastSeenAt: online ? null : row?.last_seen_at ?? (await getLastSeenAt(peerId))
    });
  }
  return snapshot;
}

export async function notifyPresenceFromClientState(
  userId: string,
  foreground: boolean
): Promise<void> {
  const peers = await getPeerUserIds(userId);
  const lastSeenAt = foreground ? null : await touchLastSeen(userId);
  for (const peerId of peers) {
    if (!(await isOperatorUser(peerId))) continue;
    sendToUser(peerId, { type: 'presence', userId, online: foreground, lastSeenAt });
  }
}

export async function notifyPresence(userId: string, online: boolean): Promise<void> {
  const peers = await getPeerUserIds(userId);
  const lastSeenAt = online ? null : await touchLastSeen(userId);
  for (const peerId of peers) {
    if (!(await isOperatorUser(peerId))) continue;
    sendToUser(peerId, { type: 'presence', userId, online, lastSeenAt });
  }
}

export function enrichPeerPresence<
  T extends {
    id: string;
    lastSeenAt?: string | null;
    clientState?: import('../db/users.js').ClientState | null;
    clientStateAt?: string | null;
  }
>(peer: T): T & { online: boolean } {
  const online = resolvePresenceOnline({
    id: peer.id,
    client_state: peer.clientState ?? null,
    client_state_at: peer.clientStateAt ?? null
  } as import('../db/users.js').UserRow);
  return {
    ...peer,
    online,
    lastSeenAt: online ? null : peer.lastSeenAt ?? null
  };
}
