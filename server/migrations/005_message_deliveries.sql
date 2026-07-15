CREATE TABLE IF NOT EXISTS message_deliveries (
  message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  delivered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (message_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_message_deliveries_message
  ON message_deliveries (message_id);
