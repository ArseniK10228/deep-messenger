import { query } from '../db/client.js';
import { broadcastToConversation, isUserSubscribedToConversation, sendToUser } from '../ws/hub.js';

/** Push chat event to subscribed clients and online members not in the chat screen. */
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
    if (!isUserSubscribedToConversation(row.user_id, conversationId)) {
      sendToUser(row.user_id, payload);
    }
  }
}
