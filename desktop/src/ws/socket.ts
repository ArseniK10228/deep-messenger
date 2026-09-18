import { wsUrl } from '../api/client';

type Handler = (data: Record<string, unknown>) => void;

export class DeepSocket {
  private ws: WebSocket | null = null;
  private handlers = new Set<Handler>();
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private subscribed: string | null = null;
  private closed = false;

  connect() {
    this.closed = false;
    if (this.ws?.readyState === WebSocket.OPEN) return;
    const url = wsUrl();
    if (!url.includes('token=') || url.endsWith('token=')) return;

    this.ws = new WebSocket(url);
    this.ws.onopen = () => {
      if (this.subscribed) {
        this.send({ type: 'subscribe', conversationId: this.subscribed });
      }
    };
    this.ws.onmessage = (ev) => {
      try {
        const data = JSON.parse(String(ev.data)) as Record<string, unknown>;
        this.handlers.forEach((h) => h(data));
      } catch {
        /* ignore */
      }
    };
    this.ws.onclose = () => {
      if (!this.closed) this.scheduleReconnect();
    };
    this.ws.onerror = () => {
      this.ws?.close();
    };
  }

  disconnect() {
    this.closed = true;
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    this.ws?.close();
    this.ws = null;
  }

  subscribe(conversationId: string | null) {
    this.subscribed = conversationId;
    if (this.ws?.readyState === WebSocket.OPEN && conversationId) {
      this.send({ type: 'subscribe', conversationId });
    }
  }

  sendTyping(conversationId: string) {
    this.send({ type: 'typing', conversationId });
  }

  sendDelivered(messageId: string) {
    this.send({ type: 'delivered', messageId });
  }

  onEvent(handler: Handler) {
    this.handlers.add(handler);
    return () => this.handlers.delete(handler);
  }

  private send(obj: Record<string, unknown>) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(obj));
    }
  }

  private scheduleReconnect() {
    if (this.reconnectTimer) return;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, 2000);
  }
}

export const globalSocket = new DeepSocket();
