import { useEffect, useState } from 'react';
import { X, ExternalLink } from 'lucide-react';
import { getToken, messageAttachmentUrl } from '../api/client';

type Props = {
  messageId: string;
  fileName: string;
  kind: string;
  onClose: () => void;
};

export function FileViewerModal({ messageId, fileName, kind, onClose }: Props) {
  const url = messageAttachmentUrl(messageId);
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const isImage = kind === 'image';
  const isPdf =
    kind === 'file' &&
    (fileName.toLowerCase().endsWith('.pdf') || mediaPath?.toLowerCase().includes('.pdf'));

  useEffect(() => {
    if (!url) {
      setError('Файл недоступен');
      setLoading(false);
      return undefined;
    }
    if (!isImage && !isPdf) {
      setLoading(false);
      return undefined;
    }
    let objectUrl: string | null = null;
    (async () => {
      try {
        const headers: HeadersInit = {};
        const token = getToken();
        if (token) headers.Authorization = `Bearer ${token}`;
        const res = await fetch(url, { headers });
        if (!res.ok) throw new Error('Не удалось загрузить');
        const blob = await res.blob();
        objectUrl = URL.createObjectURL(blob);
        setBlobUrl(objectUrl);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Ошибка загрузки');
      } finally {
        setLoading(false);
      }
    })();
    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [url, isImage, isPdf]);

  async function openWithSystem() {
    if (!url) return;
    if (window.deepDesktop?.openChatFile) {
      await window.deepDesktop.openChatFile(url, fileName, getToken());
      return;
    }
    window.open(url, '_blank', 'noopener,noreferrer');
  }

  return (
    <div className="file-viewer-backdrop anim-fade-in" onClick={onClose} role="presentation">
      <div
        className="file-viewer-panel anim-scale-in"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-label={fileName}
      >
        <header className="file-viewer-header">
          <h3>{fileName}</h3>
          <button type="button" className="btn-icon" onClick={onClose} aria-label="Закрыть">
            <X size={22} />
          </button>
        </header>
        <div className="file-viewer-body">
          {loading ? <p style={{ color: 'var(--muted)' }}>Загрузка…</p> : null}
          {error ? <p style={{ color: 'var(--error)' }}>{error}</p> : null}
          {!loading && blobUrl && isImage ? (
            <img src={blobUrl} alt={fileName} className="file-viewer-image" />
          ) : null}
          {!loading && blobUrl && isPdf ? (
            <iframe title={fileName} src={blobUrl} className="file-viewer-frame" />
          ) : null}
          {!loading && !isImage && !isPdf ? (
            <div className="file-viewer-fallback">
              <p>Просмотр в окне недоступен для этого типа файла.</p>
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => {
                  openWithSystem().catch((e) => setError(e instanceof Error ? e.message : 'Ошибка'));
                }}
              >
                <ExternalLink size={18} />
                <span>Открыть</span>
              </button>
            </div>
          ) : null}
        </div>
        {(isImage || isPdf) && url ? (
          <footer className="file-viewer-footer">
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              onClick={() => {
                openWithSystem().catch((e) => setError(e instanceof Error ? e.message : 'Ошибка'));
              }}
            >
              <ExternalLink size={16} />
              Открыть в системе
            </button>
          </footer>
        ) : null}
      </div>
    </div>
  );
}
