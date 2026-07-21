import crypto from 'crypto';
import { config } from '../config.js';

export interface IceServerConfig {
  urls: string[];
  username?: string;
  credential?: string;
}

export function buildIceServers(userId: string): IceServerConfig[] {
  const servers: IceServerConfig[] = [];

  if (config.turnSecret && config.turnUrls.length > 0) {
    const ttl = 24 * 3600;
    const expiry = Math.floor(Date.now() / 1000) + ttl;
    const username = `${expiry}:${userId}`;
    const credential = crypto
      .createHmac('sha1', config.turnSecret)
      .update(username)
      .digest('base64');

    // TURN first — required for VPN/NAT; TCP relay works when UDP is blocked.
    for (const url of config.turnUrls) {
      servers.push({ urls: [url], username, credential });
    }
  }

  for (const url of config.stunUrls) {
    servers.push({ urls: [url] });
  }

  return servers;
}
