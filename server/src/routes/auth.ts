import type { FastifyInstance } from 'fastify';
import { getAuthUser, normalizePhone } from '../lib/auth.js';
import { verifyFirebaseIdToken } from '../lib/firebase.js';
import { upsertUserByPhone, setFcmToken, searchUsersByQuery } from '../db/users.js';

export async function registerPublicAuthRoutes(app: FastifyInstance): Promise<void> {
  app.post('/auth/firebase', async (req, reply) => {
    const body = req.body as { idToken?: string; displayName?: string };
    if (!body.idToken) {
      return reply.code(400).send({ error: 'idToken required' });
    }
    try {
      const { phone } = await verifyFirebaseIdToken(body.idToken);
      const normalized = normalizePhone(phone);
      const user = await upsertUserByPhone(normalized, body.displayName?.trim());
      const token = await reply.jwtSign({
        id: user.id,
        phone: user.phone,
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
    } catch (err) {
      req.log.error(err);
      return reply.code(401).send({ error: 'Invalid Firebase token' });
    }
  });
}

export async function registerProtectedAuthRoutes(app: FastifyInstance): Promise<void> {
  app.post('/auth/fcm', async (req, reply) => {
    const user = getAuthUser(req);
    const body = req.body as { token?: string };
    if (!body.token) return reply.code(400).send({ error: 'token required' });
    await setFcmToken(user.id, body.token);
    return { ok: true };
  });

  app.get('/me', async (req) => {
    const user = getAuthUser(req);
    return { user };
  });

  app.get('/users/search', async (req, reply) => {
    const user = getAuthUser(req);
    const q = String((req.query as { q?: string }).q || '').trim();
    if (q.length < 3) {
      return reply.code(400).send({ error: 'query too short' });
    }
    const rows = await searchUsersByQuery(q, user.id);
    return {
        users: rows.map((r) => ({
          id: r.id,
          email: r.email,
          phone: r.phone || '',
          displayName: r.display_name,
          avatarUrl: r.avatar_path ? `/media/${r.avatar_path}` : null
        }))
      };
  });
}
