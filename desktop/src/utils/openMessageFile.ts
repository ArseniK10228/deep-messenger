import type { Message } from '../api/types';
import { getToken, messageAttachmentUrl } from '../api/client';

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

  if (!msg.mediaUrl && msg.kind !== 'image' && msg.kind !== 'file') return;

  const fileName = attachmentDisplayName(msg);
  const downloadUrl = messageAttachmentUrl(msg.id);
  const api = window.deepDesktop;

  if (api?.openChatFile) {
    try {
      await api.openChatFile(downloadUrl, fileName, getToken());
    } catch (e) {
      throw e instanceof Error ? e : new Error('Не удалось открыть файл');
    }
    return;
  }

  window.open(downloadUrl, '_blank', 'noopener,noreferrer');
}

function attachmentDisplayName(msg: Message): string {
  const fromPath = msg.mediaUrl?.split('/').filter(Boolean).pop();
  if (fromPath && fromPath.includes('.')) {
    try {
      return decodeURIComponent(fromPath);
    } catch {
      return fromPath;
    }
  }
  const body = msg.body?.trim();
  if (body) return body;
  return 'file';
}
