import type { FastifyInstance } from 'fastify';
import { getAuthUser } from '../lib/auth.js';
import {
  createDirectConversation,
  getConversationPeer,
  listConversationsForUser,
  userInConversation
} from '../db/conversations.js';
import {
  deleteForEveryone,
  hideMessageForUser,
  insertMessage,
  listMessages,
  markConversationRead,
  markDelivered,
  markRead
} from '../db/messages.js';
import { getUserById, mapPeerDto } from '../db/users.js';
import { pushChatEvent } from '../lib/chatPush.js';
import { notifyMessagePeers, previewText } from '../lib/messageNotify.js';
import { enrichPeerPresence, sanitizePeerPresence } from '../lib/presence.js';
import { isOperatorUser } from '../lib/operator.js';
import { query } from '../db/client.js';
import { sendToUser } from '../ws/hub.js';

export async function chatRoutes(app: FastifyInstance): Promise<void> {
  app.get('/conversations', async (req) => {
    const user = getAuthUser(req);
    const viewerIsOperator = await isOperatorUser(user.id);
    const rows = await listConversationsForUser(user.id);
    const conversations = rows.map((row) => ({
      ...row,
      peers: row.peers?.map((peer: Record<string, unknown>) => {
        const enriched = enrichPeerPresence(peer as {
          id: string;
          lastSeenAt?: string | null;
          clientState?: import('../db/users.js').ClientState | null;
          clientStateAt?: string | null;
        });
        return sanitizePeerPresence(enriched, viewerIsOperator);
      })
    }));
    return { conversations };
  });

  app.get('/conversations/:id/peer', async (req, reply) => {
    const user = getAuthUser(req);
    const { id } = req.params as { id: string };
    if (!(await userInConversation(user.id, id))) {
      return reply.code(403).send({ error: 'forbidden' });
    }
    const peer = await getConversationPeer(user.id, id);
    if (!peer) return reply.code(404).send({ error: 'peer not found' });
    const viewerIsOperator = await isOperatorUser(user.id);
    const dto = mapPeerDto(peer, viewerIsOperator);
    const enriched = enrichPeerPresence({
      ...dto,
      lastSeenAt: peer.last_seen_at,
      clientState: peer.client_state ?? null,
      clientStateAt: peer.client_state_at ?? null
    });
    return { peer: sanitizePeerPresence(enriched, viewerIsOperator) };
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
    await notifyMessagePeers({
      conversationId: id,
      senderId: user.id,
      messageId: message.id,
      senderName: user.displayName,
      preview: previewText(message)
    });
    return { message };
  });

  app.post('/messages/:id/delivered', async (req) => {
    const user = getAuthUser(req);
    const { id } = req.params as { id: string };
    const result = await markDelivered(id, user.id);
    if (result) {
      sendToUser(result.senderId, {
        type: 'message_delivered',
        messageId: id,
        conversationId: result.conversationId
      });
    }
    return { ok: true };
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

  app.post('/conversations/:id/read', async (req, reply) => {
    const user = getAuthUser(req);
    const { id } = req.params as { id: string };
    if (!(await userInConversation(user.id, id))) {
      return reply.code(403).send({ error: 'forbidden' });
    }
    const notified = await markConversationRead(id, user.id);
    for (const n of notified) {
      sendToUser(n.senderId, {
        type: 'message_read',
        messageId: n.messageId,
        conversationId: n.conversationId,
        userId: user.id
      });
    }
    return { ok: true, count: notified.length };
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
