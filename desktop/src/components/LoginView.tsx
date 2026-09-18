import { useState } from 'react';
import { sendEmailCode, setStoredUserId, setToken, verifyEmailCode } from '../api/client';

type Props = {
  onLoggedIn: () => void;
};

export function LoginView({ onLoggedIn }: Props) {
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [requestId, setRequestId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [countdown, setCountdown] = useState(0);

  async function sendCode() {
    setError(null);
    setLoading(true);
    try {
      const res = await sendEmailCode(email.trim());
      setRequestId(res.requestId);
      setCountdown(60);
      const t = setInterval(() => {
        setCountdown((c) => {
          if (c <= 1) {
            clearInterval(t);
            return 0;
          }
          return c - 1;
        });
      }, 1000);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Ошибка отправки');
    } finally {
      setLoading(false);
    }
  }

  async function verify() {
    if (!requestId) return;
    setError(null);
    setLoading(true);
    try {
      const res = await verifyEmailCode(requestId, code.trim());
      setToken(res.token);
      setStoredUserId(res.user.id);
      onLoggedIn();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Неверный код');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login">
      <div className="login-card">
        <h1>Deep</h1>
        <p>{requestId ? 'Введи код из письма' : 'Войди по email — пришлём код'}</p>
        {error ? <div className="login-error">{error}</div> : null}
        {!requestId ? (
          <>
            <input
              type="email"
              placeholder="Email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoFocus
            />
            <button className="btn btn-primary" style={{ width: '100%' }} disabled={loading || !email.trim()} onClick={sendCode}>
              {loading ? 'Отправляем…' : 'Получить код'}
            </button>
          </>
        ) : (
          <>
            <input
              placeholder="Код"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              autoFocus
            />
            <button className="btn btn-primary" style={{ width: '100%', marginBottom: 8 }} disabled={loading || code.length < 4} onClick={verify}>
              {loading ? 'Проверяем…' : 'Войти'}
            </button>
            {countdown > 0 ? (
              <p style={{ color: 'var(--muted)', fontSize: '0.85rem', margin: 0 }}>Повтор через {countdown} с</p>
            ) : (
              <button type="button" className="btn btn-ghost" style={{ width: '100%', marginTop: 8 }} onClick={sendCode}>
                Отправить снова
              </button>
            )}
          </>
        )}
      </div>
    </div>
  );
}
