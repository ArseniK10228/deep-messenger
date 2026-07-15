import type { FastifyInstance } from 'fastify';
import { normalizePhone } from '../lib/auth.js';
import {
  checkSendAbility,
  checkVerificationStatus,
  sendVerificationMessage
} from '../lib/telegramGateway.js';
import { upsertUserByPhone } from '../db/users.js';

export async function registerTelegramAuthRoutes(app: FastifyInstance): Promise<void> {
  app.post('/auth/telegram/send', async (req, reply) => {
    const body = req.body as { phone?: string };
    if (!body.phone?.trim()) {
      return reply.code(400).send({ error: 'phone required' });
    }
    let phone: string;
    try {
      phone = normalizePhone(body.phone.trim());
    } catch {
      return reply.code(400).send({ error: 'invalid phone format' });
    }

    try {
      const ability = await checkSendAbility(phone);
      const sent = await sendVerificationMessage(phone, ability.request_id);
      return {
        requestId: sent.request_id,
        phone,
        message: 'Код отправлен в Telegram'
      };
    } catch (err) {
      req.log.error(err);
      const msg = err instanceof Error ? err.message : 'send failed';
      if (msg.includes('PHONE_NUMBER_NOT_AVAILABLE') || msg.includes('not available')) {
        return reply.code(400).send({
          error: 'Номер не привязан к Telegram. Установи Telegram с этим номером.'
        });
      }
      return reply.code(502).send({ error: msg });
    }
  });

  app.post('/auth/telegram/verify', async (req, reply) => {
    const body = req.body as { requestId?: string; code?: string };
    if (!body.requestId || !body.code?.trim()) {
      return reply.code(400).send({ error: 'requestId and code required' });
    }

    const code = body.code.trim().replace(/\D/g, '');
    if (code.length < 4 || code.length > 8) {
      return reply.code(400).send({ error: 'invalid code' });
    }

    try {
      const status = await checkVerificationStatus(body.requestId, code);
      const v = status.verification_status?.status;
      if (v === 'code_invalid') {
        return reply.code(401).send({ error: 'Неверный код' });
      }
      if (v === 'code_max_attempts_exceeded') {
        return reply.code(429).send({ error: 'Слишком много попыток' });
      }
      if (v === 'expired') {
        return reply.code(410).send({ error: 'Код истёк, запроси новый' });
      }
      if (v !== 'code_valid') {
        return reply.code(401).send({ error: 'Код не подтверждён' });
      }

      const phone = status.phone_number;
      const user = await upsertUserByPhone(phone);
      const token = await reply.jwtSign({
        id: user.id,
        phone: user.phone || '',
        displayName: user.display_name
      });
      return {
        token,
        user: {
          id: user.id,
          phone: user.phone || '',
          displayName: user.display_name,
          avatarUrl: user.avatar_path ? `/media/${user.avatar_path}` : null
        }
      };
    } catch (err) {
      req.log.error(err);
      return reply.code(502).send({
        error: err instanceof Error ? err.message : 'verify failed'
      });
    }
  });
}
