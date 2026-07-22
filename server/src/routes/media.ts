import fs from 'fs';
import path from 'path';
import { randomUUID } from 'crypto';
import type { FastifyInstance } from 'fastify';
import { config } from '../config.js';
import { getAuthUser } from '../lib/auth.js';
import { userInConversation } from '../db/conversations.js';
import { insertMessage } from '../db/messages.js';
import { pushChatEvent } from '../lib/chatPush.js';
import { notifyMessagePeers, previewText } from '../lib/messageNotify.js';

const ALLOWED = new Set([
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/gif',
  'audio/ogg',
  'audio/mpeg',
  'audio/mp4',
  'application/pdf',
  'application/zip',
  'text/plain'
]);

export async function mediaRoutes(app: FastifyInstance): Promise<void> {
  app.post('/conversations/:id/upload', async (req, reply) => {
    const user = getAuthUser(req);
    const { id } = req.params as { id: string };
    if (!(await userInConversation(user.id, id))) {
      return reply.code(403).send({ error: 'forbidden' });
    }

    const part = await req.file();
    if (!part) return reply.code(400).send({ error: 'file required' });

    const mime = part.mimetype || 'application/octet-stream';
    if (!ALLOWED.has(mime)) {
      return reply.code(400).send({ error: 'unsupported file type' });
    }

    const ext = path.extname(part.filename || '') || guessExt(mime);
    const rel = path.join(id, `${randomUUID()}${ext}`);
    const abs = path.join(config.uploadDir, rel);
    fs.mkdirSync(path.dirname(abs), { recursive: true });

    const buf = await part.toBuffer();
    const maxBytes = config.maxUploadMb * 1024 * 1024;
    if (buf.length > maxBytes) {
      return reply.code(413).send({ error: 'file too large' });
    }
    fs.writeFileSync(abs, buf);

    const kind = mime.startsWith('image/')
      ? 'image'
      : mime.startsWith('audio/')
        ? 'voice'
        : 'file';

    const fields = part.fields as Record<string, { value?: string }>;
    const durationMs = fields.durationMs?.value ? Number(fields.durationMs.value) : null;
    const replyToId = fields.replyToId?.value || null;

    const message = await insertMessage({
      conversationId: id,
      senderId: user.id,
      kind,
      body: part.filename || null,
      mediaPath: rel.replace(/\\/g, '/'),
      mediaMime: mime,
      mediaSize: buf.length,
      mediaDurationMs: durationMs,
      replyToId
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
}

function guessExt(mime: string): string {
  if (mime === 'image/jpeg') return '.jpg';
  if (mime === 'image/png') return '.png';
  if (mime === 'image/webp') return '.webp';
  if (mime === 'audio/ogg') return '.ogg';
  if (mime === 'audio/mpeg') return '.mp3';
  return '.bin';
}
