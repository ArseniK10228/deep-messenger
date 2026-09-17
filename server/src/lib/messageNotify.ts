import { query } from '../db/client.js';
import { isUserOnline } from '../ws/hub.js';
import { sendMessagePush } from './firebase.js';

export function previewText(message: { kind: string; body: string | null }): string {
  if (message.kind === 'text') return message.body || '';
  if (message.kind === 'image') return 'Фото';
  if (message.kind === 'voice') return 'Голосовое сообщение';
  if (message.kind === 'video_note') return 'Видеосообщение';
  return 'Файл';
}

export async function notifyMessagePeers(input: {
  conversationId: string;
  senderId: string;
  messageId: string;
  senderName: string;
  preview: string;
}): Promise<void> {
  const r = await query<{ user_id: string; fcm_token: string | null }>(
    `SELECT cm.user_id, u.fcm_token
     FROM conversation_members cm
     JOIN users u ON u.id = cm.user_id
     WHERE cm.conversation_id = $1 AND cm.user_id <> $2 AND u.fcm_token IS NOT NULL`,
    [input.conversationId, input.senderId]
  );
  for (const row of r.rows) {
    if (isUserOnline(row.user_id)) continue;
    if (row.fcm_token) {
      await sendMessagePush(row.fcm_token, {
        conversationId: input.conversationId,
        messageId: input.messageId,
        senderName: input.senderName || 'Deep',
        preview: input.preview
      }).catch(() => {});
    }
  }
}
