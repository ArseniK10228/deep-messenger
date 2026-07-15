import { config } from '../config.js';
import { buildOtpEmailHtml, buildOtpEmailText } from './emailTemplate.js';

interface ResendResponse {
  id?: string;
  message?: string;
  name?: string;
}

export async function sendOtpEmail(to: string, code: string): Promise<void> {
  const apiKey = config.resendApiKey;
  if (!apiKey) {
    throw new Error('RESEND_NOT_CONFIGURED');
  }

  const from = `Deep Messenger <${config.resendFromEmail}>`;

  const res = await fetch('https://api.resend.com/emails', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      from,
      to: [to],
      subject: `${code} — код для входа в Deep Messenger`,
      html: buildOtpEmailHtml(code),
      text: buildOtpEmailText(code)
    })
  });

  if (!res.ok) {
    const body = (await res.json().catch(() => ({}))) as ResendResponse;
    const detail = body.message || body.name || `HTTP ${res.status}`;
    throw new Error(`RESEND_ERROR: ${detail}`);
  }
}
