-- Allow multiple email-only users (empty phone must not be unique)

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_phone_key;

UPDATE users SET phone = NULL WHERE phone = '';
