import crypto from 'crypto';
import { config } from '../config.js';
import { query } from '../db/client.js';

const OTP_TTL_MS = 10 * 60 * 1000;
const RESEND_COOLDOWN_MS = 60 * 1000;
const MAX_ATTEMPTS = 5;

export function normalizeEmail(raw: string): string {
  const email = raw.trim().toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) {
    throw new Error('invalid email');
  }
  return email;
}

export function generateOtpCode(): string {
  return String(crypto.randomInt(100000, 1000000));
}

function hashCode(code: string): string {
  return crypto.createHash('sha256').update(`${code}:${config.jwtSecret}`).digest('hex');
}

export async function createEmailOtp(email: string): Promise<{ code: string; requestId: string }> {
  const recent = await query<{ created_at: Date }>(
    `SELECT created_at FROM email_otps
     WHERE email = $1 AND created_at > now() - interval '1 minute'
     ORDER BY created_at DESC LIMIT 1`,
    [email]
  );
  if (recent.rows[0]) {
    throw new Error('RATE_LIMIT');
  }

  await query('DELETE FROM email_otps WHERE email = $1 OR expires_at < now()', [email]);

  const code = generateOtpCode();
  const expiresAt = new Date(Date.now() + OTP_TTL_MS);
  const r = await query<{ id: string }>(
    `INSERT INTO email_otps (email, code_hash, expires_at) VALUES ($1, $2, $3) RETURNING id`,
    [email, hashCode(code), expiresAt]
  );

  return { code, requestId: r.rows[0].id };
}

export async function verifyEmailOtp(
  requestId: string,
  code: string
): Promise<{ ok: true; email: string } | { ok: false; reason: 'invalid' | 'expired' | 'max_attempts' }> {
  const digits = code.replace(/\D/g, '');
  if (digits.length !== 6) {
    return { ok: false, reason: 'invalid' };
  }

  const r = await query<{
    id: string;
    email: string;
    code_hash: string;
    expires_at: Date;
    attempts: number;
  }>('SELECT * FROM email_otps WHERE id = $1', [requestId]);

  const row = r.rows[0];
  if (!row) {
    return { ok: false, reason: 'invalid' };
  }

  if (row.expires_at.getTime() < Date.now()) {
    await query('DELETE FROM email_otps WHERE id = $1', [requestId]);
    return { ok: false, reason: 'expired' };
  }

  if (row.attempts >= MAX_ATTEMPTS) {
    return { ok: false, reason: 'max_attempts' };
  }

  const valid = hashCode(digits) === row.code_hash;
  if (!valid) {
    await query('UPDATE email_otps SET attempts = attempts + 1 WHERE id = $1', [requestId]);
    return { ok: false, reason: 'invalid' };
  }

  await query('DELETE FROM email_otps WHERE id = $1', [requestId]);
  return { ok: true, email: row.email };
}

export { RESEND_COOLDOWN_MS };
