import { useEffect, useState } from 'react';
import type { User } from '../api/types';
import { createDirectChat, searchUsers } from '../api/client';
import { Avatar } from './Avatar';

type Props = {
  onClose: () => void;
  onOpen: (conversationId: string, title: string) => void;
};

export function NewChatModal({ onClose, onOpen }: Props) {
  const [q, setQ] = useState('');
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (q.trim().length < 2) {
      setUsers([]);
      return;
    }
    const t = setTimeout(async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await searchUsers(q.trim());
        setUsers(res.users);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Ошибка поиска');
      } finally {
        setLoading(false);
      }
    }, 300);
    return () => clearTimeout(t);
  }, [q]);

  async function startChat(user: User) {
    try {
      const res = await createDirectChat(user.id);
      onOpen(res.conversationId, user.displayName);
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Не удалось создать чат');
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Новый чат</h3>
        <input
          placeholder="Имя или @username"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          autoFocus
        />
        {error ? <p className="login-error">{error}</p> : null}
        {loading ? <p style={{ color: 'var(--muted)' }}>Поиск…</p> : null}
        {users.map((u) => (
          <button key={u.id} type="button" className="user-row" onClick={() => startChat(u)}>
            <Avatar name={u.displayName} size="sm" />
            <div>
              <div>{u.displayName}</div>
              {u.username ? <div style={{ fontSize: '0.8rem', color: 'var(--muted)' }}>@{u.username}</div> : null}
            </div>
          </button>
        ))}
        <button type="button" className="btn btn-ghost" style={{ width: '100%', marginTop: 12 }} onClick={onClose}>
          Закрыть
        </button>
      </div>
    </div>
  );
}
