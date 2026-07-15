-- Allow multiple email-only users (empty phone must not be unique)

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_phone_key;

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone_unique
  ON users (phone)
  WHERE phone IS NOT NULL AND phone <> '';

UPDATE users SET phone = NULL WHERE phone = '';
