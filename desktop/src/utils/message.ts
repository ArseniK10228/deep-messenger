import type { Message } from '../api/types';

export function normalizeWsMessage(raw: unknown): Message | null {
  if (!raw || typeof raw !== 'object') return null;
  const m = raw as Record<string, unknown>;
  const id = String(m.id ?? '').trim();
  if (!id) return null;
  const conversationId = String(m.conversationId ?? m.conversation_id ?? '').trim();
  const senderId = String(m.senderId ?? m.sender_id ?? '').trim();
  if (!conversationId || !senderId) return null;
  return {
    id,
    conversationId,
    senderId,
    kind: String(m.kind ?? 'text'),
    body: m.body != null ? String(m.body) : null,
    mediaUrl: (m.mediaUrl ?? m.media_url) != null ? String(m.mediaUrl ?? m.media_url) : null,
    mediaMime: (m.mediaMime ?? m.media_mime) as string | null | undefined,
    mediaSize: typeof m.mediaSize === 'number' ? m.mediaSize : undefined,
    mediaDurationMs: typeof m.mediaDurationMs === 'number' ? m.mediaDurationMs : undefined,
    createdAt: String(m.createdAt ?? m.created_at ?? new Date().toISOString())
  };
}

export function appendMessageUnique(prev: Message[], msg: Message): Message[] {
  if (prev.some((m) => m.id === msg.id)) return prev;
  return [...prev, msg];
}
