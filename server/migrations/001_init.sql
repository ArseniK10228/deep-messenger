-- Deep Messenger schema v1

CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  phone TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL DEFAULT '',
  avatar_path TEXT,
  fcm_token TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS conversations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS conversation_members (
  conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (conversation_id, user_id)
);

CREATE TABLE IF NOT EXISTS messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('text', 'image', 'file', 'voice')),
  body TEXT,
  media_path TEXT,
  media_mime TEXT,
  media_size BIGINT,
  media_duration_ms INT,
  reply_to_id UUID REFERENCES messages(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_for_all_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS message_hidden (
  message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  hidden_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (message_id, user_id)
);

CREATE TABLE IF NOT EXISTS message_reads (
  message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  read_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (message_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation_created
  ON messages (conversation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_users_phone ON users (phone);
