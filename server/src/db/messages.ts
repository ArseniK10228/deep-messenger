import { config } from '../config.js';
import { query } from './client.js';

export interface MessageRow {
  id: string;
  conversation_id: string;
  sender_id: string;
  kind: string;
  body: string | null;
  media_path: string | null;
  media_mime: string | null;
  media_size: string | null;
  media_duration_ms: number | null;
  reply_to_id: string | null;
  created_at: string;
  deleted_for_all_at: string | null;
}

function mapMessage(row: MessageRow) {
  return {
    id: row.id,
    conversationId: row.conversation_id,
    senderId: row.sender_id,
    kind: row.kind,
    body: row.body,
    mediaUrl: row.media_path ? `/media/${row.media_path}` : null,
    mediaMime: row.media_mime,
    mediaSize: row.media_size ? Number(row.media_size) : null,
    mediaDurationMs: row.media_duration_ms,
    replyToId: row.reply_to_id,
    createdAt: row.created_at,
    deletedForAllAt: row.deleted_for_all_at
  };
}

export async function listMessages(
  conversationId: string,
  userId: string,
  before?: string,
  limit = 50
) {
  const params: unknown[] = [conversationId, userId, limit];
  let cursor = '';
  if (before) {
    cursor = 'AND m.created_at < (SELECT created_at FROM messages WHERE id = $4)';
    params.push(before);
  }
  const r = await query<MessageRow>(
    `SELECT m.*
     FROM messages m
     WHERE m.conversation_id = $1
       AND m.deleted_for_all_at IS NULL
       AND NOT EXISTS (
         SELECT 1 FROM message_hidden h WHERE h.message_id = m.id AND h.user_id = $2
       )
       ${cursor}
     ORDER BY m.created_at DESC
     LIMIT $3`,
    params
  );
  return r.rows.map(mapMessage).reverse();
}

export async function insertMessage(input: {
  conversationId: string;
  senderId: string;
  kind: string;
  body?: string | null;
  mediaPath?: string | null;
  mediaMime?: string | null;
  mediaSize?: number | null;
  mediaDurationMs?: number | null;
  replyToId?: string | null;
}) {
  const r = await query<MessageRow>(
    `INSERT INTO messages (
      conversation_id, sender_id, kind, body,
      media_path, media_mime, media_size, media_duration_ms, reply_to_id
    ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)
    RETURNING *`,
    [
      input.conversationId,
      input.senderId,
      input.kind,
      input.body ?? null,
      input.mediaPath ?? null,
      input.mediaMime ?? null,
      input.mediaSize ?? null,
      input.mediaDurationMs ?? null,
      input.replyToId ?? null
    ]
  );
  return mapMessage(r.rows[0]);
}

export async function hideMessageForUser(messageId: string, userId: string): Promise<void> {
  await query(
    `INSERT INTO message_hidden (message_id, user_id) VALUES ($1, $2)
     ON CONFLICT DO NOTHING`,
    [messageId, userId]
  );
}

export async function deleteForEveryone(messageId: string, userId: string): Promise<boolean> {
  const r = await query<MessageRow>('SELECT * FROM messages WHERE id = $1', [messageId]);
  const msg = r.rows[0];
  if (!msg || msg.sender_id !== userId) return false;

  const ageMs = Date.now() - new Date(msg.created_at).getTime();
  const maxMs = config.deleteForEveryoneHours * 60 * 60 * 1000;
  if (ageMs > maxMs) return false;

  await query('UPDATE messages SET deleted_for_all_at = now() WHERE id = $1', [messageId]);
  return true;
}

export async function markRead(messageIds: string[], userId: string): Promise<void> {
  if (!messageIds.length) return;
  await query(
    `INSERT INTO message_reads (message_id, user_id)
     SELECT unnest($1::uuid[]), $2::uuid
     ON CONFLICT DO NOTHING`,
    [messageIds, userId]
  );
}

export { mapMessage };
