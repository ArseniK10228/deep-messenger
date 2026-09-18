import type { Conversation, User } from '../api/types';

export function peerFromConversation(conv: Conversation, myId: string): User | null {
  const peers = conv.peers || [];
  return peers.find((p) => p.id !== myId) || peers[0] || null;
}

export function conversationTitle(conv: Conversation, myId: string): string {
  const peer = peerFromConversation(conv, myId);
  return peer?.displayName || peer?.username || 'Чат';
}

export function lastMessagePreview(conv: Conversation): string {
  const m = conv.last_message;
  if (!m) return 'Нет сообщений';
  if (m.kind === 'text') return m.body || '';
  if (m.kind === 'image') return 'Фото';
  if (m.kind === 'voice') return 'Голосовое';
  if (m.kind === 'video_note') return 'Видеосообщение';
  if (m.kind === 'file') return m.body?.trim() ? `📎 ${m.body.trim()}` : 'Файл';
  return 'Файл';
}

export function formatListTime(iso: string | undefined): string {
  if (!iso) return '';
  try {
    const d = new Date(iso);
    const now = new Date();
    if (d.toDateString() === now.toDateString()) {
      return d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
    }
    return d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
  } catch {
    return '';
  }
}
