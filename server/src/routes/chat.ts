import type { FastifyInstance } from 'fastify';
import { getAuthUser } from '../lib/auth.js';
import {
  createDirectConversation,
  listConversationsForUser,
  userInConversation
} from '../db/conversations.js';
import {
  deleteForEveryone,
  hideMessageForUser,
  insertMessage,
  listMessages,
  markRead
} from '../db/messages.js';
import { getUserById } from '../db/users.js';
import { sendPush } from '../lib/firebase.js';
import { pushChatEvent } from '../lib/chatPush.js';
import { query } from '../db/client.js';
import { sendToUser } from '../ws/hub.js';

export async function chatRoutes(app: FastifyInstance): Promise<void> {
  app.get('/conversations', async (req) => {
    const user = getAuthUser(req);
    const rows = await listConversationsForUser(user.id);
    return { conversations: rows };
  });

  app.post('/conversations/direct', async (req, reply) => {
    const user = getAuthUser(req);
    const body = req.body as { userId?: string };
    if (!body.userId) return reply.code(400).send({ error: 'userId required' });
    if (body.userId === user.id) return reply.code(400).send({ error: 'cannot chat with self' });
    const peer = await getUserById(body.userId);
    if (!peer) return reply.code(404).send({ error: 'user not found' });
    const id = await createDirectConversation(user.id, body.userId);
    return { conversationId: id };
  });

  app.get('/conversations/:id/messages', async (req, reply) => {
    const user = getAuthUser(req);
    const { id } = req.params as { id: string };
    const q = req.query as { before?: string; limit?: string };
    if (!(await userInConversation(user.id, id))) {
      return reply.code(403).send({ error: 'forbidden' });
    }
    const messages = await listMessages(id, user.id, q.before, Number(q.limit || 50));
    return { messages };
  });

  app.post('/conversations/:id/messages', async (req, reply) => {
    const user = getAuthUser(req);
    const { id } = req.params as { id: string };
    const body = req.body as {
      kind?: string;
      body?: string;
      replyToId?: string;
    };
    if (!(await userInConversation(user.id, id))) {
      return reply.code(403).send({ error: 'forbidden' });
    }
    if (!body.kind || !['text', 'image', 'file', 'voice'].includes(body.kind)) {
      return reply.code(400).send({ error: 'invalid kind' });
    }
    if (body.kind === 'text' && !body.body?.trim()) {
      return reply.code(400).send({ error: 'empty message' });
    }
    const message = await insertMessage({
      conversationId: id,
      senderId: user.id,
      kind: body.kind,
      body: body.body?.trim() || null,
      replyToId: body.replyToId || null
    });
    await pushChatEvent(id, user.id, { type: 'message', message });
    await notifyPeers(id, user.id, user.displayName, previewText(message));
    return { message };
  });

  app.post('/messages/:id/read', async (req) => {
    const user = getAuthUser(req);
    const { id } = req.params as { id: string };
    const notified = await markRead([id], user.id);
    for (const n of notified) {
      sendToUser(n.senderId, {
        type: 'message_read',
        messageId: n.messageId,
        conversationId: n.conversationId,
        userId: user.id
      });
    }
    return { ok: true };
  });

  app.post('/messages/:id/delete', async (req, reply) => {
    const user = getAuthUser(req);
    const { id } = req.params as { id: string };
    const body = req.body as { scope?: 'me' | 'everyone' };
    if (body.scope === 'everyone') {
      const ok = await deleteForEveryone(id, user.id);
      if (!ok) return reply.code(400).send({ error: 'cannot delete for everyone' });
      const conv = await query<{ conversation_id: string }>(
        'SELECT conversation_id FROM messages WHERE id = $1',
        [id]
      );
      const conversationId = conv.rows[0]?.conversation_id;
      if (conversationId) {
        await pushChatEvent(conversationId, user.id, {
          type: 'message_deleted',
          messageId: id,
          conversationId,
          scope: 'everyone'
        });
      }
      return { ok: true };
    }
    await hideMessageForUser(id, user.id);
    return { ok: true };
  });
}

function previewText(message: { kind: string; body: string | null }): string {
  if (message.kind === 'text') return message.body || '';
  if (message.kind === 'image') return 'Фото';
  if (message.kind === 'voice') return 'Голосовое сообщение';
  return 'Файл';
}

async function notifyPeers(
  conversationId: string,
  senderId: string,
  senderName: string,
  text: string
) {
  const r = await query<{ fcm_token: string | null }>(
    `SELECT u.fcm_token
     FROM conversation_members cm
     JOIN users u ON u.id = cm.user_id
     WHERE cm.conversation_id = $1 AND cm.user_id <> $2 AND u.fcm_token IS NOT NULL`,
    [conversationId, senderId]
  );
  for (const row of r.rows) {
    if (row.fcm_token) {
      await sendPush(row.fcm_token, senderName || 'Deep', text, {
        conversationId,
        type: 'message'
      }).catch(() => {});
    }
  }
}
