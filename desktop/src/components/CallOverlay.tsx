import { Mic, MicOff, Phone, PhoneOff } from 'lucide-react';
import { useEffect, useRef } from 'react';
import { useCall } from '../call/CallContext';
import { BrandLogo } from './BrandLogo';

function formatDuration(sec: number) {
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

export function CallOverlay() {
  const { call, acceptCall, rejectCall, hangup, toggleMute, bindRemoteAudio } = useCall();
  const audioRef = useRef<HTMLAudioElement>(null);

  useEffect(() => {
    bindRemoteAudio(audioRef.current);
    return () => bindRemoteAudio(null);
  }, [bindRemoteAudio, call.phase]);

  if (call.phase === 'idle') return null;

  const status =
    call.phase === 'incoming'
      ? 'Входящий звонок'
      : call.phase === 'outgoing'
        ? 'Вызов…'
        : call.connected
          ? formatDuration(call.elapsedSec)
          : 'Соединяем…';

  return (
    <div className="call-overlay anim-fade-in">
      <audio ref={audioRef} autoPlay playsInline className="call-remote-audio" />
      <div className="call-card anim-scale-in">
        <BrandLogo size={72} showText={false} />
        <h2 className="call-peer">{call.peerName}</h2>
        <p className="call-status">{status}</p>
        {call.peerMuted && call.phase === 'active' ? (
          <p className="call-hint">Собеседник выключил микрофон</p>
        ) : null}
        <div className="call-actions">
          {call.phase === 'incoming' ? (
            <>
              <button type="button" className="call-btn reject" onClick={() => rejectCall()} aria-label="Отклонить">
                <PhoneOff size={28} />
              </button>
              <button type="button" className="call-btn accept" onClick={() => acceptCall()} aria-label="Принять">
                <Phone size={28} />
              </button>
            </>
          ) : (
            <>
              {call.phase === 'active' ? (
                <button
                  type="button"
                  className={`call-btn mute ${call.muted ? 'active' : ''}`}
                  onClick={toggleMute}
                  aria-label="Микрофон"
                >
                  {call.muted ? <MicOff size={24} /> : <Mic size={24} />}
                </button>
              ) : null}
              <button type="button" className="call-btn reject" onClick={() => hangup()} aria-label="Завершить">
                <PhoneOff size={28} />
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
