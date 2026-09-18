import { Download, RefreshCw } from 'lucide-react';
import { useEffect, useState } from 'react';

type UpdatePayload =
  | { status: 'checking' }
  | { status: 'available'; version: string }
  | { status: 'not-available' }
  | { status: 'progress'; percent: number }
  | { status: 'ready'; version: string }
  | { status: 'error'; message: string };

export function UpdateBanner() {
  const [payload, setPayload] = useState<UpdatePayload | null>(null);

  useEffect(() => {
    const api = window.deepDesktop;
    if (!api?.onUpdateStatus) return undefined;
    return api.onUpdateStatus((p) => setPayload(p as UpdatePayload));
  }, []);

  if (!payload) return null;
  if (payload.status === 'not-available' || payload.status === 'error') return null;

  if (payload.status === 'checking') {
    return (
      <div className="update-banner update-banner--muted">
        <RefreshCw size={16} className="spin" />
        <span>Проверяем обновления…</span>
      </div>
    );
  }

  if (payload.status === 'available' || payload.status === 'progress') {
    const pct = payload.status === 'progress' ? Math.round(payload.percent) : 0;
    const label =
      payload.status === 'progress'
        ? `Скачиваем обновление… ${pct}%`
        : 'Скачиваем новую версию…';
    return (
      <div className="update-banner">
        <Download size={16} />
        <span>{label}</span>
        {payload.status === 'progress' && (
          <div className="update-banner__bar">
            <div className="update-banner__bar-fill" style={{ width: `${pct}%` }} />
          </div>
        )}
      </div>
    );
  }

  if (payload.status === 'ready') {
    return (
      <div className="update-banner update-banner--ready">
        <span>Готово: v{payload.version}. Перезапустите приложение.</span>
        <button
          type="button"
          className="update-banner__btn"
          onClick={() => window.deepDesktop?.installUpdate()}
        >
          Перезапустить
        </button>
      </div>
    );
  }

  return null;
}
