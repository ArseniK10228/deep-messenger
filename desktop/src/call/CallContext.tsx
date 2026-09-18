import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode
} from 'react';
import {
  acceptCall,
  endCall,
  fetchIceServers,
  rejectCall,
  startCall as apiStartCall,
  type IceConfig
} from '../api/client';
import { globalSocket } from '../ws/socket';
import { CallEngine } from './CallEngine';

export type CallPhase = 'idle' | 'outgoing' | 'incoming' | 'active';

export type CallUi = {
  phase: CallPhase;
  callId: string | null;
  conversationId: string | null;
  peerName: string;
  connected: boolean;
  muted: boolean;
  peerMuted: boolean;
  elapsedSec: number;
};

type Ctx = {
  call: CallUi;
  startAudioCall: (conversationId: string, peerName: string) => Promise<void>;
  acceptCall: () => Promise<void>;
  rejectCall: () => Promise<void>;
  hangup: () => Promise<void>;
  toggleMute: () => void;
  bindRemoteAudio: (el: HTMLAudioElement | null) => void;
};

const defaultUi: CallUi = {
  phase: 'idle',
  callId: null,
  conversationId: null,
  peerName: '',
  connected: false,
  muted: false,
  peerMuted: false,
  elapsedSec: 0
};

const CallContext = createContext<Ctx | null>(null);

export function CallProvider({ children }: { children: ReactNode }) {
  const [call, setCall] = useState<CallUi>(defaultUi);
  const engineRef = useRef(new CallEngine());
  const iceRef = useRef<IceConfig[]>([]);
  const pendingOfferRef = useRef<{ sdp: string; sdpType: RTCSdpType } | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const callIdRef = useRef<string | null>(null);
  const phaseRef = useRef<CallPhase>('idle');

  useEffect(() => {
    callIdRef.current = call.callId;
    phaseRef.current = call.phase;
  }, [call.callId, call.phase]);

  const cleanup = useCallback(async () => {
    if (timerRef.current) clearInterval(timerRef.current);
    timerRef.current = null;
    pendingOfferRef.current = null;
    await engineRef.current.stop();
    setCall(defaultUi);
  }, []);

  const wireEngine = useCallback(() => {
    const eng = engineRef.current;
    eng.onIceCandidate = (c) => {
      const id = callIdRef.current;
      if (id && c.candidate) {
        globalSocket.send({
          type: 'call_ice',
          callId: id,
          candidate: c.candidate,
          sdpMid: c.sdpMid,
          sdpMLineIndex: c.sdpMLineIndex
        });
      }
    };
    eng.onConnectionChange = (connected) => {
      setCall((s) => ({ ...s, connected }));
      if (connected && !timerRef.current) {
        const started = Date.now();
        timerRef.current = setInterval(() => {
          setCall((s) => ({
            ...s,
            elapsedSec: Math.floor((Date.now() - started) / 1000)
          }));
        }, 500);
      }
    };
  }, []);

  const initEngine = useCallback(async () => {
    wireEngine();
    if (iceRef.current.length === 0) {
      const ice = await fetchIceServers();
      iceRef.current = ice.iceServers;
    }
    await engineRef.current.start(iceRef.current);
  }, [wireEngine]);

  const sendSdp = useCallback((callId: string, desc: RTCSessionDescriptionInit) => {
    if (!desc.sdp || !desc.type) return;
    globalSocket.send({
      type: 'call_sdp',
      callId,
      sdp: desc.sdp,
      sdpType: desc.type
    });
  }, []);

  const applyRemoteSdp = useCallback(
    async (callId: string, sdp: string, sdpType: string) => {
      const type = sdpType as RTCSdpType;
      if (phaseRef.current === 'incoming' && callIdRef.current === callId) {
        pendingOfferRef.current = { sdp, sdpType: type };
        return;
      }
      await initEngine();
      await engineRef.current.setRemoteDescription(sdp, type);
      if (type === 'offer') {
        const answer = await engineRef.current.createAnswer();
        sendSdp(callId, answer);
      }
    },
    [initEngine, sendSdp]
  );

  useEffect(() => {
    const off = globalSocket.onEvent(async (ev) => {
      const type = ev.type as string;
      if (type === 'call_invite') {
        if (ev.video === 'true' || ev.video === true) return;
        setCall({
          phase: 'incoming',
          callId: String(ev.callId || ''),
          conversationId: String(ev.conversationId || ''),
          peerName: String(ev.callerName || 'Deep'),
          connected: false,
          muted: false,
          peerMuted: false,
          elapsedSec: 0
        });
        return;
      }
      if (type === 'call_accept') {
        const callId = String(ev.callId || '');
        if (phaseRef.current !== 'outgoing' || callIdRef.current !== callId) return;
        setCall((s) => ({ ...s, phase: 'active' }));
        try {
          await initEngine();
          const offer = await engineRef.current.createOffer();
          sendSdp(callId, offer);
        } catch {
          await cleanup();
        }
        return;
      }
      if (type === 'call_sdp' && ev.sdp && ev.sdpType) {
        await applyRemoteSdp(String(ev.callId), String(ev.sdp), String(ev.sdpType));
        return;
      }
      if (type === 'call_ice' && ev.candidate && callIdRef.current === String(ev.callId)) {
        await engineRef.current.addIceCandidate(
          String(ev.candidate),
          ev.sdpMid != null ? String(ev.sdpMid) : null,
          typeof ev.sdpMLineIndex === 'number'
            ? ev.sdpMLineIndex
            : Number(ev.sdpMLineIndex ?? 0)
        );
        return;
      }
      if (type === 'call_mute') {
        setCall((s) => ({
          ...s,
          peerMuted: ev.muted === true || ev.muted === 'true'
        }));
        return;
      }
      if (type === 'call_end') {
        await cleanup();
      }
    });
    return off;
  }, [applyRemoteSdp, cleanup, initEngine, sendSdp]);

  const startAudioCall = useCallback(
    async (conversationId: string, peerName: string) => {
      if (phaseRef.current !== 'idle') return;
      setCall({
        phase: 'outgoing',
        callId: null,
        conversationId,
        peerName,
        connected: false,
        muted: false,
        peerMuted: false,
        elapsedSec: 0
      });
      const resp = await apiStartCall(conversationId, false);
      iceRef.current = resp.iceServers;
      callIdRef.current = resp.callId;
      setCall((s) => ({ ...s, callId: resp.callId }));
      wireEngine();
    },
    [wireEngine]
  );

  const accept = useCallback(async () => {
    if (phaseRef.current !== 'incoming' || !callIdRef.current) return;
    const callId = callIdRef.current;
    const muted = call.muted;
    try {
      const resp = await acceptCall(callId);
      iceRef.current = resp.iceServers;
      setCall((s) => ({ ...s, phase: 'active' }));
      await initEngine();
      const pending = pendingOfferRef.current;
      pendingOfferRef.current = null;
      if (pending) {
        await engineRef.current.setRemoteDescription(pending.sdp, pending.sdpType);
        if (pending.sdpType === 'offer') {
          const answer = await engineRef.current.createAnswer();
          sendSdp(callId, answer);
        }
      }
      globalSocket.send({ type: 'call_mute', callId, muted });
    } catch {
      await cleanup();
    }
  }, [call.muted, cleanup, initEngine, sendSdp]);

  const reject = useCallback(async () => {
    const id = callIdRef.current;
    if (id) await rejectCall(id).catch(() => {});
    await cleanup();
  }, [cleanup]);

  const hangup = useCallback(async () => {
    const id = callIdRef.current;
    if (id) await endCall(id).catch(() => {});
    await cleanup();
  }, [cleanup]);

  const toggleMute = useCallback(() => {
    setCall((s) => {
      const muted = !s.muted;
      engineRef.current.setMuted(muted);
      if (s.callId) globalSocket.send({ type: 'call_mute', callId: s.callId, muted });
      return { ...s, muted };
    });
  }, []);

  const bindRemoteAudio = useCallback((el: HTMLAudioElement | null) => {
    if (el) engineRef.current.bindRemoteElement(el);
  }, []);

  return (
    <CallContext.Provider
      value={{
        call,
        startAudioCall,
        acceptCall: accept,
        rejectCall: reject,
        hangup,
        toggleMute,
        bindRemoteAudio
      }}
    >
      {children}
    </CallContext.Provider>
  );
}

export function useCall() {
  const ctx = useContext(CallContext);
  if (!ctx) throw new Error('useCall outside provider');
  return ctx;
}
