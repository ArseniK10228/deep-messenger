import fs from 'fs';
import Fastify from 'fastify';
import cors from '@fastify/cors';
import jwt from '@fastify/jwt';
import multipart from '@fastify/multipart';
import fastifyStatic from '@fastify/static';
import path from 'path';
import type { FastifyReply, FastifyRequest } from 'fastify';
import { config } from './config.js';
import { initFirebase } from './lib/firebase.js';
import { registerPublicAuthRoutes, registerProtectedAuthRoutes } from './routes/auth.js';
import { chatRoutes } from './routes/chat.js';
import { mediaRoutes } from './routes/media.js';
import { attachWebSocket } from './ws/server.js';

async function authenticate(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  try {
    await req.jwtVerify();
  } catch {
    reply.code(401).send({ error: 'Unauthorized' });
  }
}

async function main() {
  fs.mkdirSync(config.uploadDir, { recursive: true });
  initFirebase();

  const app = Fastify({ logger: true });

  await app.register(cors, { origin: true });
  await app.register(jwt, { secret: config.jwtSecret });
  await app.register(multipart, { limits: { fileSize: config.maxUploadMb * 1024 * 1024 } });
  await app.register(fastifyStatic, {
    root: path.resolve(config.uploadDir),
    prefix: '/media/',
    decorateReply: false
  });

  app.get('/health', async () => ({ ok: true, service: 'deep-messenger', version: '0.1.0' }));

  await app.register(async (api) => {
    await registerPublicAuthRoutes(api);
  }, { prefix: '/api/v1' });

  await app.register(async (api) => {
    api.addHook('onRequest', authenticate);
    await registerProtectedAuthRoutes(api);
    await chatRoutes(api);
    await mediaRoutes(api);
  }, { prefix: '/api/v1' });

  await app.listen({ port: config.port, host: config.host });
  attachWebSocket(app.server, app);
  app.log.info(`Deep Messenger API listening on :${config.port}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
