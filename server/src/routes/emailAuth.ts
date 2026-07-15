import type { FastifyInstance } from 'fastify';
import { createEmailOtp, normalizeEmail, verifyEmailOtp } from '../lib/emailOtp.js';
import { sendOtpEmail } from '../lib/resend.js';
import { upsertUserByEmail } from '../db/users.js';

export async function registerEmailAuthRoutes(app: FastifyInstance): Promise<void> {
  app.post('/auth/email/send', async (req, reply) => {
    const body = req.body as { email?: string };
    if (!body.email?.trim()) {
      return reply.code(400).send({ error: 'email required' });
    }

    let email: string;
    try {
      email = normalizeEmail(body.email);
    } catch {
      return reply.code(400).send({ error: 'Некорректный email' });
    }

    try {
      const { code, requestId } = await createEmailOtp(email);
      await sendOtpEmail(email, code);
      return {
        requestId,
        email,
        message: 'Код отправлен на email'
      };
    } catch (err) {
      req.log.error(err);
      const msg = err instanceof Error ? err.message : 'send failed';
      if (msg === 'RATE_LIMIT') {
        return reply.code(429).send({ error: 'Подожди минуту перед повторной отправкой' });
      }
      return reply.code(502).send({ error: 'Не удалось отправить письмо' });
    }
  });

  app.post('/auth/email/verify', async (req, reply) => {
    const body = req.body as { requestId?: string; code?: string };
    if (!body.requestId || !body.code?.trim()) {
      return reply.code(400).send({ error: 'requestId and code required' });
    }

    const result = await verifyEmailOtp(body.requestId, body.code.trim());
    if (!result.ok) {
      if (result.reason === 'expired') {
        return reply.code(410).send({ error: 'Код истёк, запроси новый' });
      }
      if (result.reason === 'max_attempts') {
        return reply.code(429).send({ error: 'Слишком много попыток' });
      }
      return reply.code(401).send({ error: 'Неверный код' });
    }

    const user = await upsertUserByEmail(result.email);
    const token = await reply.jwtSign({
      id: user.id,
      email: user.email,
      phone: user.phone || '',
      displayName: user.display_name
    });

    return {
      token,
      user: {
        id: user.id,
        email: user.email,
        phone: user.phone || '',
        displayName: user.display_name,
        avatarUrl: user.avatar_path ? `/media/${user.avatar_path}` : null
      }
    };
  });
}
