import type { FastifyInstance } from 'fastify';
import { getAuthUser } from '../lib/auth.js';
import {
  createCall,
  getCall,
  peerUserId,
  setCallState,
  userInCall
} from '../lib/callRegistry.js';
import { buildIceServers } from '../lib/turn.js';
import { userInConversation } from '../db/conversations.js';
import { query } from '../db/client.js';
import { sendCallPush } from '../lib/firebase.js';
import { sendToUser } from '../ws/hub.js';
import { notifyAdminUsers } from '../lib/adminMonitor.js';

async function getPeerUserId(conversationId: string, userId: string): Promise<string | null> {
  const r = await query<{ user_id: string }>(
    `SELECT user_id FROM conversation_members
     WHERE conversation_id = $1 AND user_id <> $2
     LIMIT 1`,
    [conversationId, userId]
  );
  return r.rows[0]?.user_id || null;
}

async function getUserDisplayName(userId: string): Promise<string> {
  const r = await query<{ display_name: string; phone: string }>(
    'SELECT display_name, phone FROM users WHERE id = $1',
    [userId]
  );
  const row = r.rows[0];
  if (!row) return 'Deep';
  return row.display_name?.trim() || row.phone || 'Deep';
}

async function notifyCallEnded(peerId: string, callId: string, reason: string): Promise<void> {
  sendToUser(peerId, { type: 'call_end', callId, reason });
  await sendCallPush(peerId, { type: 'call_ended', callId, reason }).catch(() => {});
}

export async function callRoutes(app: FastifyInstance): Promise<void> {
  app.get('/calls/ice', async (req) => {
    const user = getAuthUser(req);
    return { iceServers: buildIceServers(user.id) };
  });

  app.post('/calls', async (req, reply) => {
    const user = getAuthUser(req);
    const body = req.body as { conversationId?: string; video?: boolean };
    if (!body.conversationId) {
      return reply.code(400).send({ error: 'conversationId required' });
    }
    const isVideo = body.video === true;
    if (!(await userInConversation(user.id, body.conversationId))) {
      return reply.code(403).send({ error: 'forbidden' });
    }
    const calleeId = await getPeerUserId(body.conversationId, user.id);
    if (!calleeId) return reply.code(404).send({ error: 'peer not found' });

    const call = createCall({
      conversationId: body.conversationId,
      callerId: user.id,
      calleeId
    });
    const callerName = await getUserDisplayName(user.id);

    sendToUser(calleeId, {
      type: 'call_invite',
      callId: call.id,
      conversationId: call.conversationId,
      callerId: user.id,
      callerName,
      video: isVideo ? 'true' : 'false'
    });

    // FCM backup: WS may look online while the app is dozed or the socket is stale.
    await sendCallPush(calleeId, {
      type: 'incoming_call',
      callId: call.id,
      conversationId: call.conversationId,
      callerId: user.id,
      callerName,
      video: isVideo ? 'true' : 'false'
    }).catch(() => {});

    await notifyAdminUsers([user.id, calleeId]);

    return {
      callId: call.id,
      iceServers: buildIceServers(user.id)
    };
  });

  app.post('/calls/:id/accept', async (req, reply) => {
    const user = getAuthUser(req);
    const { id } = req.params as { id: string };
    const call = getCall(id);
    if (!call || !userInCall(id, user.id)) {
      return reply.code(404).send({ error: 'call not found' });
    }
    if (call.calleeId !== user.id) {
      return reply.code(403).send({ error: 'only callee can accept' });
    }
    setCallState(id, 'active');
    sendToUser(call.callerId, { type: 'call_accept', callId: id });
    await notifyAdminUsers([call.callerId, call.calleeId]);
    return { ok: true, iceServers: buildIceServers(user.id) };
  });

  app.post('/calls/:id/reject', async (req, reply) => {
    const user = getAuthUser(req);
    const { id } = req.params as { id: string };
    const call = getCall(id);
    if (!call || !userInCall(id, user.id)) {
      return reply.code(404).send({ error: 'call not found' });
    }
    setCallState(id, 'ended');
    const peer = peerUserId(call, user.id);
    if (peer) {
      await notifyCallEnded(peer, id, 'reject');
    }
    await notifyAdminUsers([call.callerId, call.calleeId]);
    return { ok: true };
  });

  app.post('/calls/:id/end', async (req, reply) => {
    const user = getAuthUser(req);
    const { id } = req.params as { id: string };
    const call = getCall(id);
    if (!call || !userInCall(id, user.id)) {
      return reply.code(404).send({ error: 'call not found' });
    }
    setCallState(id, 'ended');
    const peer = peerUserId(call, user.id);
    if (peer) {
      await notifyCallEnded(peer, id, 'hangup');
    }
    await notifyAdminUsers([call.callerId, call.calleeId]);
    return { ok: true };
  });
}
