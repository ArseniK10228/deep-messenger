CREATE TABLE IF NOT EXISTS call_recordings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  call_id TEXT NOT NULL,
  conversation_id UUID REFERENCES conversations(id) ON DELETE SET NULL,
  caller_id UUID REFERENCES users(id) ON DELETE SET NULL,
  callee_id UUID REFERENCES users(id) ON DELETE SET NULL,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at TIMESTAMPTZ,
  duration_ms INT,
  media_path TEXT,
  media_mime TEXT DEFAULT 'audio/mp4',
  media_size BIGINT,
  video BOOLEAN NOT NULL DEFAULT false,
  uploaded_by UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS call_recordings_call_id_idx ON call_recordings(call_id);
CREATE INDEX IF NOT EXISTS call_recordings_started_at_idx ON call_recordings(started_at DESC);
