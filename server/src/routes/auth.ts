import type { FastifyInstance } from 'fastify';
import { getAuthUser, normalizePhone } from '../lib/auth.js';
import { verifyFirebaseIdToken } from '../lib/firebase.js';
import {
  getUserById,
  mapUserDto,
  searchUsersByQuery,
  setFcmToken,
  setClientVersion,
  updateUserProfile,
  upsertUserByPhone
} from '../db/users.js';
import { notifyAdminUserUpdate } from '../lib/adminMonitor.js';
import { notifyPresenceFromClientState, touchLastSeen } from '../lib/presence.js';

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
        phone: user.phone || '',
        displayName: user.display_name
      });
      return {
        token,
        user: mapUserDto(user)
      };
    } catch (err) {
      req.log.error(err);
      return reply.code(401).send({ error: 'Invalid Firebase token' });
    }
  });
}

export async function registerProtectedAuthRoutes(app: FastifyInstance): Promise<void> {
  app.post('/auth/refresh', async (req, reply) => {
    const auth = getAuthUser(req);
    const row = await getUserById(auth.id);
    if (!row) return reply.code(404).send({ error: 'user not found' });
    const token = await reply.jwtSign({
      id: auth.id,
      phone: auth.phone || '',
      displayName: auth.displayName
    });
    return { token, user: mapUserDto(row) };
  });

  app.post('/auth/fcm', async (req, reply) => {
    const user = getAuthUser(req);
    const body = req.body as { token?: string; versionCode?: number; versionName?: string };
    if (!body.token) return reply.code(400).send({ error: 'token required' });
    await setFcmToken(user.id, body.token, body.versionCode ?? null, body.versionName ?? null);
    return { ok: true };
  });

  app.post('/auth/client', async (req, reply) => {
    const user = getAuthUser(req);
    const body = req.body as {
      versionCode?: number;
      versionName?: string;
      foreground?: boolean;
      batteryPct?: number;
      charging?: boolean;
      network?: string;
      inCall?: boolean;
    };
    if (!body.versionCode || !body.versionName?.trim()) {
      return reply.code(400).send({ error: 'versionCode and versionName required' });
    }
    const clientState =
      body.foreground === undefined &&
      body.batteryPct === undefined &&
      body.charging === undefined &&
      !body.network &&
      body.inCall === undefined
        ? null
        : {
            foreground: body.foreground,
            batteryPct: body.batteryPct,
            charging: body.charging,
            network: body.network?.trim() || undefined,
            inCall: body.inCall
          };
    await setClientVersion(user.id, body.versionCode, body.versionName, clientState);
    const inCall = body.inCall === true;
    if (body.foreground === false && !inCall) {
      await touchLastSeen(user.id);
    }
    if (body.foreground !== undefined) {
      const presenceOnline = body.foreground || inCall;
      await notifyPresenceFromClientState(user.id, presenceOnline);
    }
    await notifyAdminUserUpdate(user.id);
    return { ok: true };
  });

  app.get('/me', async (req, reply) => {
    const auth = getAuthUser(req);
    const row = await getUserById(auth.id);
    if (!row) return reply.code(404).send({ error: 'user not found' });
    return { user: mapUserDto(row) };
  });

  app.patch('/me', async (req, reply) => {
    const auth = getAuthUser(req);
    const body = req.body as { displayName?: string; username?: string };
    try {
      const row = await updateUserProfile(auth.id, body);
      return { user: mapUserDto(row) };
    } catch (err) {
      const code = err instanceof Error ? err.message : '';
      if (code === 'INVALID_USERNAME') {
        return reply.code(400).send({ error: 'username: 3–32 символа, латиница, цифры и _' });
      }
      if (code === 'USERNAME_TAKEN') {
        return reply.code(409).send({ error: 'Этот username уже занят' });
      }
      throw err;
    }
  });

  app.get('/users/search', async (req, reply) => {
    const user = getAuthUser(req);
    const q = String((req.query as { q?: string }).q || '').trim();
    if (q.length < 2) {
      return reply.code(400).send({ error: 'query too short' });
    }
    const rows = await searchUsersByQuery(q, user.id);
    return { users: rows.map((r) => mapUserDto(r)) };
  });
}
