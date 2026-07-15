export function buildOtpEmailHtml(code: string): string {
  const year = new Date().getFullYear();
  return `<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Код для входа</title>
</head>
<body style="margin:0;padding:0;background:#0f1117;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#0f1117;padding:32px 16px;">
    <tr>
      <td align="center">
        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:440px;background:#171a22;border-radius:16px;border:1px solid #2a2f3d;overflow:hidden;">
          <tr>
            <td style="padding:28px 28px 8px;text-align:center;">
              <div style="font-size:22px;font-weight:700;color:#6ee7b7;letter-spacing:0.5px;">Deep Messenger</div>
            </td>
          </tr>
          <tr>
            <td style="padding:8px 28px 0;text-align:center;">
              <h1 style="margin:0;font-size:20px;line-height:1.4;color:#f3f4f6;font-weight:600;">Код для входа</h1>
            </td>
          </tr>
          <tr>
            <td style="padding:12px 28px 0;text-align:center;">
              <p style="margin:0;font-size:15px;line-height:1.5;color:#9ca3af;">Введите этот код в приложении Deep Messenger:</p>
            </td>
          </tr>
          <tr>
            <td style="padding:24px 28px;text-align:center;">
              <div style="display:inline-block;padding:16px 28px;background:#111827;border:1px solid #374151;border-radius:12px;font-size:32px;font-weight:700;letter-spacing:8px;color:#f9fafb;font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace;">
                ${code}
              </div>
            </td>
          </tr>
          <tr>
            <td style="padding:0 28px 24px;text-align:center;">
              <p style="margin:0;font-size:13px;line-height:1.5;color:#6b7280;">Код действует <strong style="color:#9ca3af;">10 минут</strong>. Никому не сообщайте этот код.</p>
            </td>
          </tr>
          <tr>
            <td style="padding:0 28px 28px;text-align:center;">
              <p style="margin:0;font-size:12px;line-height:1.5;color:#4b5563;">Если вы не запрашивали вход, просто проигнорируйте это письмо.</p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 28px;background:#12151c;border-top:1px solid #2a2f3d;text-align:center;">
              <p style="margin:0;font-size:11px;color:#4b5563;">© ${year} Deep Messenger · deepdesignpc.online</p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>`;
}

export function buildOtpEmailText(code: string): string {
  return `Deep Messenger

Код для входа: ${code}

Код действует 10 минут. Никому не сообщайте этот код.

Если вы не запрашивали вход, проигнорируйте это письмо.`;
}
