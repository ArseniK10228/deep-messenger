import { config } from '../config.js';

const BASE = 'https://gatewayapi.telegram.org';

interface GatewayResult<T> {
  ok: boolean;
  result?: T;
  error?: string;
}

export interface RequestStatus {
  request_id: string;
  phone_number: string;
  verification_status?: {
    status: 'code_valid' | 'code_invalid' | 'code_max_attempts_exceeded' | 'expired';
  };
}

async function gatewayCall<T>(method: string, body: Record<string, unknown>): Promise<T> {
  const token = config.telegramGatewayToken;
  if (!token) {
    throw new Error('TELEGRAM_GATEWAY_TOKEN not configured');
  }

  const res = await fetch(`${BASE}/${method}`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(body)
  });

  const data = (await res.json()) as GatewayResult<T>;
  if (!data.ok || !data.result) {
    throw new Error(data.error || `Telegram Gateway ${method} failed`);
  }
  return data.result;
}

export async function checkSendAbility(phoneE164: string): Promise<RequestStatus> {
  return gatewayCall<RequestStatus>('checkSendAbility', { phone_number: phoneE164 });
}

export async function sendVerificationMessage(
  phoneE164: string,
  requestId: string
): Promise<RequestStatus> {
  const body: Record<string, unknown> = {
    phone_number: phoneE164,
    request_id: requestId,
    code_length: 6,
    ttl: 300
  };
  if (config.telegramGatewaySender) {
    body.sender_username = config.telegramGatewaySender;
  }
  return gatewayCall<RequestStatus>('sendVerificationMessage', body);
}

export async function checkVerificationStatus(
  requestId: string,
  code: string
): Promise<RequestStatus> {
  return gatewayCall<RequestStatus>('checkVerificationStatus', {
    request_id: requestId,
    code
  });
}
