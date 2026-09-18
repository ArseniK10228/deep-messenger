import { useEffect, useState } from 'react';
import { X, ExternalLink } from 'lucide-react';
import { mediaUrl } from '../api/client';

type Props = {
  fileName: string;
  mediaPath: string | null | undefined;
  kind: string;
  onClose: () => void;
};

export function FileViewerModal({ fileName, mediaPath, kind, onClose }: Props) {
  const url = mediaUrl(mediaPath);
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
        const res = await fetch(url);
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

  function openExternal() {
    if (!url) return;
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
              <button type="button" className="btn btn-primary" onClick={openExternal}>
                <ExternalLink size={18} />
                <span>Открыть / скачать</span>
              </button>
            </div>
          ) : null}
        </div>
        {(isImage || isPdf) && url ? (
          <footer className="file-viewer-footer">
            <button type="button" className="btn btn-ghost btn-sm" onClick={openExternal}>
              <ExternalLink size={16} />
              В браузере
            </button>
          </footer>
        ) : null}
      </div>
    </div>
  );
}
