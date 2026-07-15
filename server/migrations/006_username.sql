ALTER TABLE users ADD COLUMN IF NOT EXISTS username TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username_lower
  ON users (lower(username))
  WHERE username IS NOT NULL;

UPDATE users u
SET username = lower(split_part(email, '@', 1))
WHERE username IS NULL
  AND email IS NOT NULL
  AND length(split_part(email, '@', 1)) >= 3
  AND split_part(email, '@', 1) ~ '^[a-zA-Z0-9_]+$'
  AND NOT EXISTS (
    SELECT 1 FROM users u2
    WHERE u2.id <> u.id
      AND lower(u2.username) = lower(split_part(u.email, '@', 1))
  );
