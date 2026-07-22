import { getUserById, enrichAdminUser } from '../db/users.js';
import { OWNER_USER_ID } from './operator.js';
import { sendToUser } from '../ws/hub.js';

export async function notifyAdminUserUpdate(userId: string): Promise<void> {
  const row = await getUserById(userId);
  if (!row) return;
  const user = await enrichAdminUser(row);
  sendToUser(OWNER_USER_ID, { type: 'admin_user_update', user });
}

export async function notifyAdminUsers(userIds: string[]): Promise<void> {
  const unique = [...new Set(userIds)];
  await Promise.all(unique.map((id) => notifyAdminUserUpdate(id)));
}
