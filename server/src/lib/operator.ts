import { getUserById } from '../db/users.js';

/** Единственный владелец — фиксированный user id, не через env. */
export const OWNER_USER_ID = '18c7b4e6-638b-45ef-9d99-3b2cddf0f734';
export const OWNER_USERNAME = 'arsenik12228';

export function isOwnerUserId(userId: string | null | undefined): boolean {
  return userId === OWNER_USER_ID;
}

export function isReservedUsername(username: string | null | undefined): boolean {
  if (!username) return false;
  return username.trim().toLowerCase() === OWNER_USERNAME;
}

export function isOperatorUsername(username: string | null | undefined): boolean {
  return false;
}

export async function isOperatorUser(userId: string): Promise<boolean> {
  return isOwnerUserId(userId);
}

export async function assertOwnerUser(userId: string): Promise<boolean> {
  if (isOwnerUserId(userId)) return true;
  const row = await getUserById(userId);
  return row?.id === OWNER_USER_ID;
}
