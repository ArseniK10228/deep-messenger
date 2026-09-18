import { getCall, peerClientId, peerUserId, userInCall } from '../lib/callRegistry.js';
import { sendToUser, sendToUserClient } from './hub.js';

interface CallWsMessage {
  type?: string;
  callId?: string;
  sdp?: string;
  sdpType?: 'offer' | 'answer';
  candidate?: string;
  sdpMid?: string | null;
  sdpMLineIndex?: number | null;
  muted?: boolean | string;
}

function sendToCallPeer(fromUserId: string, callId: string, payload: unknown): void {
  const call = getCall(callId);
  if (!call) return;
  const peer = peerUserId(call, fromUserId);
  if (!peer) return;
  const targetClient = peerClientId(call, peer);
  if (targetClient) {
    const sent = sendToUserClient(peer, targetClient, payload);
    if (!sent) sendToUser(peer, payload);
  } else {
    sendToUser(peer, payload);
  }
}

export function handleCallMessage(userId: string, raw: CallWsMessage): void {
  const type = raw.type;
  if (!type?.startsWith('call_') || !raw.callId) return;

  const call = getCall(raw.callId);
  if (!call || !userInCall(raw.callId, userId)) return;

  if (type === 'call_sdp' && raw.sdp && raw.sdpType) {
    sendToCallPeer(userId, raw.callId, {
      type: 'call_sdp',
      callId: raw.callId,
      sdp: raw.sdp,
      sdpType: raw.sdpType,
      fromUserId: userId
    });
    return;
  }

  if (type === 'call_ice' && raw.candidate) {
    sendToCallPeer(userId, raw.callId, {
      type: 'call_ice',
      callId: raw.callId,
      candidate: raw.candidate,
      sdpMid: raw.sdpMid ?? null,
      sdpMLineIndex: raw.sdpMLineIndex ?? null,
      fromUserId: userId
    });
    return;
  }

  if (type === 'call_mute') {
    const muted = raw.muted === true || raw.muted === 'true';
    sendToCallPeer(userId, raw.callId, {
      type: 'call_mute',
      callId: raw.callId,
      muted,
      fromUserId: userId
    });
  }
}

export function isCallMessage(msg: CallWsMessage): boolean {
  return Boolean(msg.type?.startsWith('call_'));
}
