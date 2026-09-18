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

export async function mediaRoutes(app: FastifyInstance): Promise<void> {
  app.post('/conversations/:id/upload', async (req, reply) => {
    const user = getAuthUser(req);
    const { id } = req.params as { id: string };
    if (!(await userInConversation(user.id, id))) {
      return reply.code(403).send({ error: 'forbidden' });
    }

    const part = await req.file();
    if (!part) return reply.code(400).send({ error: 'file required' });

    const mime = (part.mimetype || 'application/octet-stream').split(';')[0].trim().toLowerCase();

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

    const fields = part.fields as Record<string, { value?: string }>;
    const forceVideoNote =
      fields.videoNote?.value === '1' || fields.videoNote?.value === 'true';
    const forceVoice = fields.voice?.value === '1' || fields.voice?.value === 'true';
    const durationMs = fields.durationMs?.value ? Number(fields.durationMs.value) : null;
    const replyToId = fields.replyToId?.value || null;

    const kind = resolveMessageKind(mime, { forceVideoNote, forceVoice, durationMs });
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

function resolveMessageKind(
  mime: string,
  flags: { forceVideoNote: boolean; forceVoice: boolean; durationMs: number | null }
): string {
  if (flags.forceVideoNote && mime.startsWith('video/')) return 'video_note';
  if (
    flags.forceVoice ||
    (flags.durationMs != null && flags.durationMs > 0 && mime.startsWith('audio/'))
  ) {
    return 'voice';
  }
  if (mime.startsWith('image/')) return 'image';
  return 'file';
}

function guessExt(mime: string): string {
  const map: Record<string, string> = {
    'image/jpeg': '.jpg',
    'image/png': '.png',
    'image/webp': '.webp',
    'image/gif': '.gif',
    'audio/ogg': '.ogg',
    'audio/mpeg': '.mp3',
    'audio/mp4': '.m4a',
    'audio/wav': '.wav',
    'audio/x-wav': '.wav',
    'video/mp4': '.mp4',
    'video/webm': '.webm',
    'application/pdf': '.pdf',
    'application/zip': '.zip',
    'application/x-zip-compressed': '.zip',
    'application/x-rar-compressed': '.rar',
    'application/vnd.rar': '.rar',
    'application/x-7z-compressed': '.7z',
    'text/plain': '.txt',
    'application/msword': '.doc',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document': '.docx',
    'application/vnd.ms-excel': '.xls',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': '.xlsx',
    'application/octet-stream': '.bin'
  };
  return map[mime] || '.bin';
}
