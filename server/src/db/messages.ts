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
  peer_delivered?: boolean;
  peer_read?: boolean;
}

function mapMessage(row: MessageRow, viewerId?: string) {
  const base = {
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
  if (viewerId && row.sender_id === viewerId) {
    return {
      ...base,
      peerDelivered: row.peer_delivered ?? false,
      peerRead: row.peer_read ?? false
    };
  }
  return base;
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
    `SELECT m.*,
       CASE WHEN m.sender_id = $2 THEN EXISTS (
         SELECT 1 FROM message_deliveries d
         JOIN conversation_members cm
           ON cm.conversation_id = m.conversation_id AND cm.user_id <> m.sender_id
         WHERE d.message_id = m.id AND d.user_id = cm.user_id
       ) ELSE false END AS peer_delivered,
       CASE WHEN m.sender_id = $2 THEN EXISTS (
         SELECT 1 FROM message_reads r
         JOIN conversation_members cm
           ON cm.conversation_id = m.conversation_id AND cm.user_id <> m.sender_id
         WHERE r.message_id = m.id AND r.user_id = cm.user_id
       ) ELSE false END AS peer_read
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
  return r.rows.reverse().map((row) => mapMessage(row, userId));
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
  return mapMessage(r.rows[0], input.senderId);
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

export async function markDelivered(
  messageId: string,
  userId: string
): Promise<{ senderId: string; conversationId: string } | null> {
  const r = await query<{ sender_id: string; conversation_id: string }>(
    `SELECT m.sender_id, m.conversation_id FROM messages m
     JOIN conversation_members cm ON cm.conversation_id = m.conversation_id AND cm.user_id = $2
     WHERE m.id = $1 AND m.sender_id <> $2`,
    [messageId, userId]
  );
  const row = r.rows[0];
  if (!row) return null;

  await query(
    `INSERT INTO message_deliveries (message_id, user_id) VALUES ($1, $2)
     ON CONFLICT DO NOTHING`,
    [messageId, userId]
  );
  return { senderId: row.sender_id, conversationId: row.conversation_id };
}

export async function markRead(
  messageIds: string[],
  userId: string
): Promise<Array<{ messageId: string; senderId: string; conversationId: string }>> {
  if (!messageIds.length) return [];

  const r = await query<{ id: string; sender_id: string; conversation_id: string }>(
    `SELECT m.id, m.sender_id, m.conversation_id FROM messages m
     JOIN conversation_members cm ON cm.conversation_id = m.conversation_id AND cm.user_id = $2
     WHERE m.id = ANY($1::uuid[]) AND m.sender_id <> $2`,
    [messageIds, userId]
  );
  const ids = r.rows.map((row) => row.id);
  if (ids.length) {
    await query(
      `INSERT INTO message_deliveries (message_id, user_id)
       SELECT unnest($1::uuid[]), $2::uuid
       ON CONFLICT DO NOTHING`,
      [ids, userId]
    );
    await query(
      `INSERT INTO message_reads (message_id, user_id)
       SELECT unnest($1::uuid[]), $2::uuid
       ON CONFLICT DO NOTHING`,
      [ids, userId]
    );
  }
  return r.rows.map((row) => ({
    messageId: row.id,
    senderId: row.sender_id,
    conversationId: row.conversation_id
  }));
}

export async function markConversationRead(
  conversationId: string,
  userId: string
): Promise<Array<{ messageId: string; senderId: string; conversationId: string }>> {
  const r = await query<{ id: string; sender_id: string }>(
    `SELECT m.id, m.sender_id FROM messages m
     JOIN conversation_members cm ON cm.conversation_id = m.conversation_id AND cm.user_id = $2
     WHERE m.conversation_id = $1 AND m.sender_id <> $2
       AND m.deleted_for_all_at IS NULL
       AND NOT EXISTS (
         SELECT 1 FROM message_reads mr WHERE mr.message_id = m.id AND mr.user_id = $2
       )`,
    [conversationId, userId]
  );
  const ids = r.rows.map((row) => row.id);
  if (!ids.length) return [];

  await query(
    `INSERT INTO message_deliveries (message_id, user_id)
     SELECT unnest($1::uuid[]), $2::uuid
     ON CONFLICT DO NOTHING`,
    [ids, userId]
  );
  await query(
    `INSERT INTO message_reads (message_id, user_id)
     SELECT unnest($1::uuid[]), $2::uuid
     ON CONFLICT DO NOTHING`,
    [ids, userId]
  );
  return r.rows.map((row) => ({
    messageId: row.id,
    senderId: row.sender_id,
    conversationId
  }));
}

export async function listMessagesAdmin(
  conversationId: string,
  before?: string,
  limit = 100
) {
  const params: unknown[] = [conversationId, limit];
  let cursor = '';
  if (before) {
    cursor = 'AND m.created_at < (SELECT created_at FROM messages WHERE id = $3)';
    params.push(before);
  }
  const r = await query<MessageRow>(
    `SELECT m.*
     FROM messages m
     WHERE m.conversation_id = $1
       ${cursor}
     ORDER BY m.created_at DESC
     LIMIT $2`,
    params
  );
  return r.rows.reverse().map((row) => mapMessage(row));
}

export { mapMessage };
