import { getCall, peerUserId, userInCall } from '../lib/callRegistry.js';
import { sendToUser } from './hub.js';

interface CallWsMessage {
  type?: string;
  callId?: string;
  sdp?: string;
  sdpType?: 'offer' | 'answer';
  candidate?: string;
  sdpMid?: string | null;
  sdpMLineIndex?: number | null;
}

export function handleCallMessage(userId: string, raw: CallWsMessage): void {
  const type = raw.type;
  if (!type?.startsWith('call_') || !raw.callId) return;

  const call = getCall(raw.callId);
  if (!call || !userInCall(raw.callId, userId)) return;

  const peer = peerUserId(call, userId);
  if (!peer) return;

  if (type === 'call_sdp' && raw.sdp && raw.sdpType) {
    sendToUser(peer, {
      type: 'call_sdp',
      callId: raw.callId,
      sdp: raw.sdp,
      sdpType: raw.sdpType,
      fromUserId: userId
    });
    return;
  }

  if (type === 'call_ice' && raw.candidate) {
    sendToUser(peer, {
      type: 'call_ice',
      callId: raw.callId,
      candidate: raw.candidate,
      sdpMid: raw.sdpMid ?? null,
      sdpMLineIndex: raw.sdpMLineIndex ?? null,
      fromUserId: userId
    });
  }
}

export function isCallMessage(msg: CallWsMessage): boolean {
  return Boolean(msg.type?.startsWith('call_'));
}
