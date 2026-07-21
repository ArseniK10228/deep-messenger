import type { Server } from 'http';
import { WebSocketServer } from 'ws';
import type { FastifyInstance } from 'fastify';
import {
  broadcastToConversation,
  isUserOnline,
  registerClient,
  sendToUser,
  subscribeConversation,
  unregisterClient
} from './hub.js';
import { markDelivered } from '../db/messages.js';
import { handleCallMessage, isCallMessage } from './callSignaling.js';
import {
  buildPresenceSnapshot,
  notifyPresence,
  touchLastSeen
} from '../lib/presence.js';

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
      const wasOnline = isUserOnline(payload.id);
      const client = registerClient(ws, payload.id);
      if (!wasOnline) {
        await touchLastSeen(payload.id);
        await notifyPresence(payload.id, true);
      }
      ws.send(
        JSON.stringify({
          type: 'presence_snapshot',
          users: await buildPresenceSnapshot(payload.id)
        })
      );

      ws.on('message', async (raw) => {
        try {
          const msg = JSON.parse(String(raw)) as {
            type?: string;
            conversationId?: string;
            messageId?: string;
          };
          if (isCallMessage(msg)) {
            handleCallMessage(payload.id, msg);
            return;
          }
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
          if (msg.type === 'delivered' && msg.messageId) {
            const result = await markDelivered(msg.messageId, payload.id);
            if (result) {
              sendToUser(result.senderId, {
                type: 'message_delivered',
                messageId: msg.messageId,
                conversationId: result.conversationId
              });
            }
          }
        } catch {
          /* ignore malformed */
        }
      });

      ws.on('close', async () => {
        unregisterClient(client);
        if (!isUserOnline(payload.id)) {
          await notifyPresence(payload.id, false);
        }
      });
    } catch {
      ws.close(4401, 'unauthorized');
    }
  });
}
