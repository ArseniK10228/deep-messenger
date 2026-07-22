import type { ClientState, UserRow } from '../db/users.js';
import { isUserOnline } from '../ws/hub.js';

export const CLIENT_STATE_TTL_MS = 120_000;

export function isUserForegroundOnline(row: {
  client_state?: ClientState | null;
  client_state_at?: string | null;
}): boolean {
  if (row.client_state?.foreground !== true) return false;
  if (!row.client_state_at) return false;
  const age = Date.now() - new Date(row.client_state_at).getTime();
  return age >= 0 && age <= CLIENT_STATE_TTL_MS;
}

export function resolvePresenceOnline(row: UserRow): boolean {
  if (row.client_state_at != null) {
    return isUserForegroundOnline(row);
  }
  return isUserOnline(row.id);
}
