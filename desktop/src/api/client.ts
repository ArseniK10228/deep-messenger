import type { Conversation, Message, User } from './types';
import { getClientId } from '../utils/clientId';

export const API_BASE =
  import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ||
  'https://api.deepdesignpc.online';

const API = `${API_BASE}/api/v1`;

let token: string | null = localStorage.getItem('deep_token');

export function getToken() {
  return token;
}

export function setToken(t: string | null) {
  token = t;
  if (t) localStorage.setItem('deep_token', t);
  else localStorage.removeItem('deep_token');
}

export function getStoredUserId() {
  return localStorage.getItem('deep_user_id');
}

export function setStoredUserId(id: string | null) {
  if (id) localStorage.setItem('deep_user_id', id);
  else localStorage.removeItem('deep_user_id');
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  auth = true
): Promise<T> {
  const headers = new Headers(init.headers);
  if (!headers.has('Content-Type') && init.body && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  if (auth && token) headers.set('Authorization', `Bearer ${token}`);

  const res = await fetch(`${API}${path}`, { ...init, headers });
  const text = await res.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = { raw: text };
    }
  }
  if (!res.ok) {
    const err = data as { error?: string };
    throw new Error(err?.error || `HTTP ${res.status}`);
  }
  return data as T;
}

export async function sendEmailCode(email: string) {
  return request<{ requestId: string; email: string }>('/auth/email/send', {
    method: 'POST',
    body: JSON.stringify({ email })
  }, false);
}

export async function verifyEmailCode(requestId: string, code: string) {
  return request<{ token: string; user: User }>('/auth/email/verify', {
    method: 'POST',
    body: JSON.stringify({ requestId, code })
  }, false);
}

export async function fetchMe() {
  return request<{ user: User }>('/me');
}

export async function fetchConversations() {
  return request<{ conversations: Conversation[] }>('/conversations');
}

export async function fetchMessages(conversationId: string) {
  return request<{ messages: Message[] }>(
    `/conversations/${conversationId}/messages?limit=80`
  );
}

export async function sendTextMessage(conversationId: string, body: string) {
  return request<{ message: Message }>(`/conversations/${conversationId}/messages`, {
    method: 'POST',
    body: JSON.stringify({ kind: 'text', body })
  });
}

export async function uploadMedia(
  conversationId: string,
  file: File,
  durationMs?: number,
  videoNote = false
) {
  const form = new FormData();
  form.append('file', file);
  if (durationMs != null) form.append('durationMs', String(durationMs));
  if (videoNote) form.append('videoNote', '1');
  return request<{ message: Message }>(`/conversations/${conversationId}/upload`, {
    method: 'POST',
    body: form
  });
}

export async function markConversationRead(conversationId: string) {
  return request(`/conversations/${conversationId}/read`, { method: 'POST' });
}

export async function markDelivered(messageId: string) {
  return request(`/messages/${messageId}/delivered`, { method: 'POST' });
}

export async function searchUsers(q: string) {
  return request<{ users: User[] }>(`/users/search?q=${encodeURIComponent(q)}`);
}

export async function createDirectChat(userId: string) {
  return request<{ conversationId: string }>('/conversations/direct', {
    method: 'POST',
    body: JSON.stringify({ userId })
  });
}

export type IceConfig = {
  urls: string | string[];
  username?: string;
  credential?: string;
};

export async function fetchIceServers() {
  return request<{ iceServers: IceConfig[] }>('/calls/ice');
}

export async function startCall(conversationId: string, video = false) {
  return request<{ callId: string; iceServers: IceConfig[] }>('/calls', {
    method: 'POST',
    body: JSON.stringify({ conversationId, video, clientId: getClientId() })
  });
}

export async function acceptCall(callId: string) {
  return request<{ ok: boolean; iceServers: IceConfig[] }>(`/calls/${callId}/accept`, {
    method: 'POST',
    body: JSON.stringify({ clientId: getClientId() })
  });
}

export async function rejectCall(callId: string) {
  return request(`/calls/${callId}/reject`, { method: 'POST' });
}

export async function endCall(callId: string) {
  return request(`/calls/${callId}/end`, { method: 'POST' });
}

export async function reportClient(foreground: boolean, inCall = false) {
  return request('/auth/client', {
    method: 'POST',
    body: JSON.stringify({
      versionCode: Number(import.meta.env.VITE_APP_VERSION_CODE) || 0,
      versionName: `${import.meta.env.VITE_APP_VERSION}-desktop`,
      foreground,
      network: 'desktop',
      inCall
    })
  });
}

export function mediaUrl(path: string | null | undefined): string | null {
  if (!path) return null;
  if (path.startsWith('http')) return path;
  return `${API_BASE}${path.startsWith('/') ? '' : '/'}${path}`;
}

export function wsUrl(): string {
  const base = API_BASE.replace(/^https:/, 'wss:').replace(/^http:/, 'ws:');
  return `${base}/ws?token=${encodeURIComponent(token || '')}&clientId=${encodeURIComponent(getClientId())}`;
}
