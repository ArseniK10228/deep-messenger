import { randomUUID } from 'crypto';

export type CallState = 'ringing' | 'active' | 'ended';

export interface CallSession {
  id: string;
  conversationId: string;
  callerId: string;
  calleeId: string;
  state: CallState;
  createdAt: number;
}

const calls = new Map<string, CallSession>();
const byUser = new Map<string, string>();

export function createCall(input: {
  conversationId: string;
  callerId: string;
  calleeId: string;
}): CallSession {
  endCallsForUser(input.callerId);
  endCallsForUser(input.calleeId);

  const session: CallSession = {
    id: randomUUID(),
    conversationId: input.conversationId,
    callerId: input.callerId,
    calleeId: input.calleeId,
    state: 'ringing',
    createdAt: Date.now()
  };
  calls.set(session.id, session);
  byUser.set(input.callerId, session.id);
  byUser.set(input.calleeId, session.id);
  return session;
}

export function getCall(callId: string): CallSession | undefined {
  return calls.get(callId);
}

export function getActiveCallForUser(userId: string): CallSession | undefined {
  const id = byUser.get(userId);
  if (!id) return undefined;
  const call = calls.get(id);
  if (!call || call.state === 'ended') return undefined;
  return call;
}

export function userInCall(callId: string, userId: string): boolean {
  const call = calls.get(callId);
  if (!call) return false;
  return call.callerId === userId || call.calleeId === userId;
}

export function peerUserId(call: CallSession, userId: string): string | null {
  if (call.callerId === userId) return call.calleeId;
  if (call.calleeId === userId) return call.callerId;
  return null;
}

export function setCallState(callId: string, state: CallState): CallSession | undefined {
  const call = calls.get(callId);
  if (!call) return undefined;
  call.state = state;
  if (state === 'ended') {
    byUser.delete(call.callerId);
    byUser.delete(call.calleeId);
    setTimeout(() => calls.delete(callId), 60_000);
  }
  return call;
}

function endCallsForUser(userId: string): void {
  const existing = getActiveCallForUser(userId);
  if (existing) setCallState(existing.id, 'ended');
}
