import type { Server } from 'http';
import { WebSocketServer } from 'ws';
import type { FastifyInstance } from 'fastify';
import { broadcastToConversation, registerClient, subscribeConversation, unregisterClient } from './hub.js';

export function attachWebSocket(server: Server, app: FastifyInstance): void {
  const wss = new WebSocketServer({ server, path: '/ws' });

  wss.on('connection', async (ws, req) => {
    try {
      const url = new URL(req.url || '', 'http://localhost');
      const token = url.searchParams.get('token') || '';
      if (!token) {
        ws.close(4401, 'token required');
        return;
      }
      const payload = await app.jwt.verify<{ id: string }>(token);
      const client = registerClient(ws, payload.id);

      ws.on('message', (raw) => {
        try {
          const msg = JSON.parse(String(raw)) as { type?: string; conversationId?: string };
          if (msg.type === 'subscribe' && msg.conversationId) {
            subscribeConversation(client, msg.conversationId);
            ws.send(JSON.stringify({ type: 'subscribed', conversationId: msg.conversationId }));
          }
          if (msg.type === 'typing' && msg.conversationId) {
            broadcastToConversation(msg.conversationId, {
              type: 'typing',
              conversationId: msg.conversationId,
              userId: payload.id
            });
          }
        } catch {
          /* ignore malformed */
        }
      });

      ws.on('close', () => unregisterClient(client));
    } catch {
      ws.close(4401, 'unauthorized');
    }
  });
}
