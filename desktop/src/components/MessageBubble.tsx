import { useRef, useState } from 'react';
import type { Message } from '../api/types';
import { mediaUrl } from '../api/client';

type Props = {
  msg: Message;
  mine: boolean;
  onOpenMedia?: (msg: Message) => void;
};

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatTime(iso: string) {
  try {
    return new Date(iso).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
  } catch {
    return '';
  }
}

export function MessageBubble({ msg, mine, onOpenMedia }: Props) {
  const audioRef = useRef<HTMLAudioElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const [playing, setPlaying] = useState(false);

  const body = () => {
    switch (msg.kind) {
      case 'image': {
        const url = mediaUrl(msg.mediaUrl);
        if (!url) return <p className="bubble-text">📷 Фото</p>;
        return (
          <button
            type="button"
            className="media-open-btn"
            onClick={() => onOpenMedia?.(msg)}
          >
            <img src={url} alt="" loading="lazy" />
          </button>
        );
      }
      case 'voice': {
        const url = mediaUrl(msg.mediaUrl);
        const sec = Math.max(1, Math.round((msg.mediaDurationMs || 0) / 1000));
        return (
          <div className="voice-msg">
            <button
              type="button"
              className="btn btn-primary"
              style={{ width: 40, height: 40, borderRadius: '50%', padding: 0 }}
              onClick={() => {
                const a = audioRef.current;
                if (!a || !url) return;
                if (playing) {
                  a.pause();
                  setPlaying(false);
                } else {
                  a.src = url;
                  a.play();
                  setPlaying(true);
                }
              }}
            >
              {playing ? '❚❚' : '▶'}
            </button>
            <span className="bubble-text">{sec}s</span>
            <audio
              ref={audioRef}
              onEnded={() => setPlaying(false)}
              preload="none"
            />
          </div>
        );
      }
      case 'video_note': {
        const url = mediaUrl(msg.mediaUrl);
        return (
          <div
            className="video-note"
            onClick={() => {
              const v = videoRef.current;
              if (!v || !url) return;
              if (v.paused) {
                v.src = url;
                v.play();
                setPlaying(true);
              } else {
                v.pause();
                setPlaying(false);
              }
            }}
          >
            {url ? (
              <video ref={videoRef} playsInline muted={false} onEnded={() => setPlaying(false)} />
            ) : null}
            {!playing && <span style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 32 }}>▶</span>}
          </div>
        );
      }
      case 'file': {
        const url = mediaUrl(msg.mediaUrl);
        const name = msg.body?.trim() || 'Файл';
        const size =
          msg.mediaSize != null && msg.mediaSize > 0
            ? formatFileSize(msg.mediaSize)
            : null;
        return (
          <button
            type="button"
            className="file-attachment"
            onClick={() => onOpenMedia?.(msg)}
            disabled={!url}
          >
            <span className="file-attachment-icon">📎</span>
            <span className="file-attachment-meta">
              <span className="file-attachment-name">{name}</span>
              {size ? <span className="file-attachment-size">{size}</span> : null}
            </span>
          </button>
        );
      }
      default:
        return <p className="bubble-text">{msg.body || ''}</p>;
    }
  };

  return (
    <div className={`msg-row ${mine ? 'mine' : 'theirs'}`}>
      <div className={`bubble ${mine ? 'mine' : 'theirs'}`}>
        {body()}
        <div className="bubble-meta">{formatTime(msg.createdAt)}</div>
      </div>
    </div>
  );
}
