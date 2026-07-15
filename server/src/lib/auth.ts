import type { FastifyRequest } from 'fastify';

export interface AuthUser {
  id: string;
  phone: string;
  email?: string;
  displayName: string;
}

export function getAuthUser(req: FastifyRequest): AuthUser {
  const u = req.user as AuthUser | undefined;
  if (!u?.id) {
    throw new Error('Unauthorized');
  }
  return u;
}

export function normalizePhone(raw: string): string {
  const digits = raw.replace(/\D/g, '');
  if (digits.startsWith('8') && digits.length === 11) {
    return `+7${digits.slice(1)}`;
  }
  if (digits.startsWith('7') && digits.length === 11) {
    return `+${digits}`;
  }
  if (raw.startsWith('+')) return `+${digits}`;
  throw new Error('Invalid phone format');
}
