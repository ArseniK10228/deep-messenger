import { query } from './client.js';

export interface ConversationRow {
  id: string;
}

export async function findDirectConversation(userA: string, userB: string): Promise<string | null> {
  const r = await query<{ id: string }>(
    `SELECT c.id
     FROM conversations c
     JOIN conversation_members m1 ON m1.conversation_id = c.id AND m1.user_id = $1
     JOIN conversation_members m2 ON m2.conversation_id = c.id AND m2.user_id = $2
     GROUP BY c.id
     HAVING COUNT(*) = 2
     LIMIT 1`,
    [userA, userB]
  );
  return r.rows[0]?.id || null;
}

export async function createDirectConversation(userA: string, userB: string): Promise<string> {
  const existing = await findDirectConversation(userA, userB);
  if (existing) return existing;

  const conv = await query<ConversationRow>(
    `INSERT INTO conversations DEFAULT VALUES RETURNING id`
  );
  const id = conv.rows[0].id;
  await query(
    `INSERT INTO conversation_members (conversation_id, user_id) VALUES ($1, $2), ($1, $3)`,
    [id, userA, userB]
  );
  return id;
}

export async function userInConversation(userId: string, conversationId: string): Promise<boolean> {
  const r = await query(
    `SELECT 1 FROM conversation_members WHERE conversation_id = $1 AND user_id = $2`,
    [conversationId, userId]
  );
  return r.rowCount !== null && r.rowCount > 0;
}

export async function listConversationsForUser(userId: string) {
  const r = await query(
    `SELECT c.id,
            (
              SELECT row_to_json(m.*)
              FROM (
                SELECT id, kind, body, media_path, sender_id, created_at, deleted_for_all_at
                FROM messages
                WHERE conversation_id = c.id
                  AND deleted_for_all_at IS NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM message_hidden h
                    WHERE h.message_id = messages.id AND h.user_id = $1
                  )
                ORDER BY created_at DESC
                LIMIT 1
              ) m
            ) AS last_message,
            (
              SELECT json_agg(json_build_object(
                'id', u.id,
                'email', u.email,
                'phone', COALESCE(u.phone, ''),
                'username', u.username,
                'displayName', u.display_name,
                'avatarUrl', CASE WHEN u.avatar_path IS NOT NULL THEN '/media/' || u.avatar_path END
              ))
              FROM conversation_members cm
              JOIN users u ON u.id = cm.user_id
              WHERE cm.conversation_id = c.id AND cm.user_id <> $1
            ) AS peers
     FROM conversations c
     JOIN conversation_members me ON me.conversation_id = c.id AND me.user_id = $1
     ORDER BY (
       SELECT created_at FROM messages
       WHERE conversation_id = c.id
       ORDER BY created_at DESC LIMIT 1
     ) DESC NULLS LAST`,
    [userId]
  );
  return r.rows;
}
