import type { WebSocket } from 'ws';

interface WsClient {
  ws: WebSocket;
  userId: string;
  clientId: string;
  conversationIds: Set<string>;
}

const clients = new Set<WsClient>();

export function registerClient(ws: WebSocket, userId: string, clientId: string): WsClient {
  const client: WsClient = { ws, userId, clientId, conversationIds: new Set() };
  clients.add(client);
  return client;
}

export function unregisterClient(client: WsClient): void {
  clients.delete(client);
}

export function subscribeConversation(client: WsClient, conversationId: string): void {
  client.conversationIds.add(conversationId);
}

export function broadcastToConversation(conversationId: string, payload: unknown): void {
  const data = JSON.stringify(payload);
  for (const c of clients) {
    if (conversationId === '*' || c.conversationIds.has(conversationId)) {
      if (c.ws.readyState === c.ws.OPEN) {
        c.ws.send(data);
      }
    }
  }
}

export function isUserOnline(userId: string): boolean {
  for (const c of clients) {
    if (
      c.userId === userId &&
      c.ws.readyState === c.ws.OPEN &&
      c.conversationIds.size === 0
    ) {
      return true;
    }
  }
  return false;
}

export function sendToUser(userId: string, payload: unknown): void {
  const data = JSON.stringify(payload);
  for (const c of clients) {
    if (c.userId === userId && c.ws.readyState === c.ws.OPEN) {
      c.ws.send(data);
    }
  }
}

export function sendToUserClient(userId: string, clientId: string, payload: unknown): void {
  const data = JSON.stringify(payload);
  for (const c of clients) {
    if (
      c.userId === userId &&
      c.clientId === clientId &&
      c.ws.readyState === c.ws.OPEN
    ) {
      c.ws.send(data);
    }
  }
}

export function sendToUserExceptClient(
  userId: string,
  exceptClientId: string,
  payload: unknown
): void {
  const data = JSON.stringify(payload);
  for (const c of clients) {
    if (
      c.userId === userId &&
      c.clientId !== exceptClientId &&
      c.ws.readyState === c.ws.OPEN
    ) {
      c.ws.send(data);
    }
  }
}

export function isUserSubscribedToConversation(userId: string, conversationId: string): boolean {
  for (const c of clients) {
    if (c.userId === userId && c.conversationIds.has(conversationId)) {
      return true;
    }
  }
  return false;
}
