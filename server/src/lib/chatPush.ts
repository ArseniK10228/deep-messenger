import { query } from '../db/client.js';
import { broadcastToConversation, sendToUser } from '../ws/hub.js';

/** Push chat event to subscribed clients and all online conversation members. */
export async function pushChatEvent(
  conversationId: string,
  senderId: string,
  payload: unknown
): Promise<void> {
  broadcastToConversation(conversationId, payload);
  const r = await query<{ user_id: string }>(
    `SELECT user_id FROM conversation_members
     WHERE conversation_id = $1 AND user_id <> $2`,
    [conversationId, senderId]
  );
  for (const row of r.rows) {
    sendToUser(row.user_id, payload);
  }
}
