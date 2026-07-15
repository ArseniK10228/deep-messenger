import { config } from '../config.js';
import { buildOtpEmailHtml, buildOtpEmailText } from './emailTemplate.js';

interface ResendResponse {
  id?: string;
  message?: string;
}

export async function sendOtpEmail(to: string, code: string): Promise<void> {
  const apiKey = config.resendApiKey;
  if (!apiKey) {
    throw new Error('RESEND_API_KEY not configured');
  }

  const res = await fetch('https://api.resend.com/emails', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      from: config.resendFrom,
      to: [to],
      subject: `${code} — код для входа в Deep Messenger`,
      html: buildOtpEmailHtml(code),
      text: buildOtpEmailText(code)
    })
  });

  if (!res.ok) {
    const body = (await res.json().catch(() => ({}))) as ResendResponse;
    throw new Error(body.message || `Resend API error ${res.status}`);
  }
}
