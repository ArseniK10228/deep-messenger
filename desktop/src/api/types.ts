export type User = {
  id: string;
  email?: string | null;
  phone: string;
  username?: string | null;
  displayName: string;
  avatarUrl?: string | null;
  online?: boolean | null;
  lastSeenAt?: string | null;
};

export type Message = {
  id: string;
  conversationId: string;
  senderId: string;
  kind: string;
  body: string | null;
  mediaUrl: string | null;
  mediaMime?: string | null;
  mediaSize?: number | null;
  mediaDurationMs?: number | null;
  createdAt: string;
  peerDelivered?: boolean;
  peerRead?: boolean;
};

export type LastMessage = {
  id: string;
  kind: string;
  body: string | null;
  sender_id: string;
  created_at: string;
};

export type Conversation = {
  id: string;
  last_message: LastMessage | null;
  peers: User[] | null;
};
