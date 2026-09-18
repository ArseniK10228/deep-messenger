import type { Message } from '../api/types';
import { getToken, mediaUrl } from '../api/client';

function fileExt(name: string): string {
  const i = name.lastIndexOf('.');
  return i >= 0 ? name.slice(i).toLowerCase() : '';
}

/** Preview in modal (image / PDF). Everything else opens with the OS — like Telegram. */
export function shouldPreviewInApp(msg: Message): boolean {
  if (msg.kind === 'image') return true;
  const name = msg.body?.trim() || '';
  return fileExt(name) === '.pdf' || msg.mediaUrl?.toLowerCase().includes('.pdf') === true;
}

export async function openMessageFile(
  msg: Message,
  showViewer: (m: Message) => void
): Promise<void> {
  if (shouldPreviewInApp(msg)) {
    showViewer(msg);
    return;
  }

  const url = mediaUrl(msg.mediaUrl);
  if (!url) return;

  const fileName = msg.body?.trim() || 'file';
  const api = window.deepDesktop;

  if (api?.openChatFile) {
    try {
      await api.openChatFile(url, fileName, getToken());
    } catch (e) {
      throw e instanceof Error ? e : new Error('Не удалось открыть файл');
    }
    return;
  }

  window.open(url, '_blank', 'noopener,noreferrer');
}
