import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import fs from 'fs';
import path from 'path';
import { randomUUID } from 'crypto';
import { getAuthUser } from '../lib/auth.js';
import {
  getUserById,
  listUsersAdmin,
  enrichAdminUser
} from '../db/users.js';
import { enrichPeerPresence } from '../lib/presence.js';
import { notifyAdminUserUpdate } from '../lib/adminMonitor.js';
import { isOperatorUser } from '../lib/operator.js';
import { sendToUser } from '../ws/hub.js';
import { listConversationsForUserAdmin } from '../db/conversations.js';
import { listMessagesAdmin } from '../db/messages.js';
import {
  getCallRecording,
  insertCallRecording,
  listCallRecordingsEnriched,
  enrichCallRecording
} from '../db/callRecordings.js';
import { getCall } from '../lib/callRegistry.js';
import { getConversationPeer } from '../db/conversations.js';
import { config } from '../config.js';

async function requireOwner(req: FastifyRequest, reply: FastifyReply) {
  const viewer = getAuthUser(req);
  if (!(await isOperatorUser(viewer.id))) {
    reply.code(403).send({ error: 'forbidden' });
    return null;
  }
  return viewer;
}

export async function adminRoutes(app: FastifyInstance): Promise<void> {
  app.get('/admin/users', async (req, reply) => {
    if (!(await requireOwner(req, reply))) return;
    const rows = await listUsersAdmin();
    const users = await Promise.all(rows.map((row) => enrichAdminUser(row)));
    return { users };
  });

  app.get('/admin/users/:userId', async (req, reply) => {
    if (!(await requireOwner(req, reply))) return;
    const { userId } = req.params as { userId: string };
    const row = await getUserById(userId);
    if (!row) return reply.code(404).send({ error: 'user not found' });
    return {
      user: enrichPeerPresence(await enrichAdminUser(row))
    };
  });

  app.get('/admin/users/:userId/conversations', async (req, reply) => {
    if (!(await requireOwner(req, reply))) return;
    const { userId } = req.params as { userId: string };
    const row = await getUserById(userId);
    if (!row) return reply.code(404).send({ error: 'user not found' });
    const conversations = await listConversationsForUserAdmin(userId);
    return { conversations };
  });

  app.get('/admin/conversations/:id/messages', async (req, reply) => {
    if (!(await requireOwner(req, reply))) return;
    const { id } = req.params as { id: string };
    const q = req.query as { before?: string; limit?: string };
    const messages = await listMessagesAdmin(id, q.before, Number(q.limit || 100));
    return { messages };
  });

  app.get('/admin/call-recordings', async (req, reply) => {
    if (!(await requireOwner(req, reply))) return;
    const q = req.query as { limit?: string };
    const rows = await listCallRecordingsEnriched(Number(q.limit || 100));
    return { recordings: rows };
  });

  app.get('/admin/call-recordings/:id', async (req, reply) => {
    if (!(await requireOwner(req, reply))) return;
    const { id } = req.params as { id: string };
    const row = await getCallRecording(id);
    if (!row) return reply.code(404).send({ error: 'not found' });
    return { recording: await enrichCallRecording(row) };
  });

  app.post('/admin/users/:userId/diag', async (req, reply) => {
    if (!(await requireOwner(req, reply))) return;
    const { userId } = req.params as { userId: string };
    const body = req.body as { action?: string };
    const action = body.action || 'snapshot';
    sendToUser(userId, {
      type: 'diag_request',
      action,
      operatorId: (getAuthUser(req)).id
    });
    return { ok: true };
  });
}

export async function callRecordingRoutes(app: FastifyInstance): Promise<void> {
  app.post('/calls/recordings', async (req, reply) => {
    const user = getAuthUser(req);
    const part = await req.file();
    if (!part) return reply.code(400).send({ error: 'file required' });

    const fields = part.fields as Record<string, { value?: string }>;
    const callId = fields.callId?.value?.trim();
    if (!callId) return reply.code(400).send({ error: 'callId required' });

    const conversationId = fields.conversationId?.value || null;
    let callerId = fields.callerId?.value || null;
    let calleeId = fields.calleeId?.value || null;
    const startedAt = fields.startedAt?.value || null;
    const endedAt = fields.endedAt?.value || null;
    const durationMs = fields.durationMs?.value ? Number(fields.durationMs.value) : null;
    const video = fields.video?.value === 'true';

    const call = getCall(callId);
    if (call) {
      callerId = callerId ?? call.callerId;
      calleeId = calleeId ?? call.calleeId;
    } else if (conversationId && (!callerId || !calleeId)) {
      const peer = await getConversationPeer(user.id, conversationId);
      if (peer) {
        callerId = callerId ?? user.id;
        calleeId = calleeId ?? peer.id;
      }
    }

    const mime = part.mimetype || 'audio/mp4';
    const ext = mime.includes('wav') ? '.wav' : mime.includes('mp4') ? '.m4a' : mime.includes('mpeg') ? '.mp3' : '.ogg';
    const rel = path.join('recordings', `${randomUUID()}${ext}`);
    const abs = path.join(config.uploadDir, rel);
    fs.mkdirSync(path.dirname(abs), { recursive: true });

    const buf = await part.toBuffer();
    const maxBytes = config.maxUploadMb * 1024 * 1024;
    if (buf.length > maxBytes) {
      return reply.code(413).send({ error: 'file too large' });
    }
    fs.writeFileSync(abs, buf);

    const recording = await insertCallRecording({
      callId,
      conversationId,
      callerId,
      calleeId,
      startedAt: startedAt ?? undefined,
      endedAt: endedAt ?? undefined,
      durationMs,
      mediaPath: rel.replace(/\\/g, '/'),
      mediaMime: mime,
      mediaSize: buf.length,
      video,
      uploadedBy: user.id
    });

    await notifyAdminUserUpdate(user.id);

    return { recording: await enrichCallRecording(recording) };
  });
}
