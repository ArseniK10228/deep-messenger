import { useCallback, useEffect, useRef, useState } from 'react';
import type { Conversation, Message, User } from '../api/types';
import {
  fetchConversations,
  fetchMe,
  fetchMessages,
  getStoredUserId,
  getToken,
  markConversationRead,
  markDelivered,
  reportClient,
  sendTextMessage,
  setToken,
  uploadMedia
} from '../api/client';
import { globalSocket } from '../ws/socket';
import { conversationTitle, formatListTime, lastMessagePreview, peerFromConversation } from '../utils/chat';
import { Avatar } from './Avatar';
import { MessageBubble } from './MessageBubble';
import { NewChatModal } from './NewChatModal';

export function MainApp() {
  const [me, setMe] = useState<User | null>(null);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [activeTitle, setActiveTitle] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [draft, setDraft] = useState('');
  const [loadingList, setLoadingList] = useState(true);
  const [loadingChat, setLoadingChat] = useState(false);
  const [typing, setTyping] = useState(false);
  const [showNewChat, setShowNewChat] = useState(false);
  const [listFilter, setListFilter] = useState('');
  const [error, setError] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const typingTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const myId = me?.id || getStoredUserId() || '';

  const loadList = useCallback(async () => {
    try {
      const res = await fetchConversations();
      setConversations(res.conversations);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Ошибка загрузки чатов');
    } finally {
      setLoadingList(false);
    }
  }, []);

  const openChat = useCallback(async (id: string, title: string) => {
    setActiveId(id);
    setActiveTitle(title);
    setLoadingChat(true);
    setError(null);
    globalSocket.subscribe(id);
    try {
      const res = await fetchMessages(id);
      setMessages(res.messages);
      await markConversationRead(id);
      for (const m of res.messages) {
        if (m.senderId !== myId) {
          markDelivered(m.id).catch(() => {});
          globalSocket.sendDelivered(m.id);
        }
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Ошибка загрузки сообщений');
    } finally {
      setLoadingChat(false);
    }
  }, [myId]);

  useEffect(() => {
    fetchMe()
      .then((r) => setMe(r.user))
      .catch(() => {
        setToken(null);
        window.location.reload();
      });
    loadList();
    globalSocket.connect();

    const off = globalSocket.onEvent((ev) => {
      const type = ev.type as string;
      if (type === 'message' && ev.message) {
        const msg = ev.message as Message;
        setConversations((prev) => {
          const copy = [...prev];
          const idx = copy.findIndex((c) => c.id === msg.conversationId);
          if (idx >= 0) {
            copy[idx] = {
              ...copy[idx],
              last_message: {
                id: msg.id,
                kind: msg.kind,
                body: msg.body,
                sender_id: msg.senderId,
                created_at: msg.createdAt
              }
            };
            const [item] = copy.splice(idx, 1);
            copy.unshift(item);
          }
          return copy;
        });
        if (msg.conversationId === activeId) {
          setMessages((prev) => (prev.some((m) => m.id === msg.id) ? prev : [...prev, msg]));
          if (msg.senderId !== myId) {
            markDelivered(msg.id).catch(() => {});
            globalSocket.sendDelivered(msg.id);
            markConversationRead(msg.conversationId).catch(() => {});
          }
        }
      }
      if (type === 'typing' && ev.conversationId === activeId) {
        setTyping(true);
        if (typingTimer.current) clearTimeout(typingTimer.current);
        typingTimer.current = setTimeout(() => setTyping(false), 3000);
      }
      if (type === 'presence' && ev.userId) {
        setConversations((prev) =>
          prev.map((c) => ({
            ...c,
            peers: c.peers?.map((p) =>
              p.id === ev.userId
                ? { ...p, online: ev.online as boolean, lastSeenAt: ev.lastSeenAt as string }
                : p
            ) || null
          }))
        );
      }
    });

    const heartbeat = setInterval(() => {
      reportClient(true).catch(() => {});
    }, 30_000);
    reportClient(true).catch(() => {});

    return () => {
      off();
      clearInterval(heartbeat);
      globalSocket.disconnect();
    };
  }, [activeId, loadList, myId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, activeId]);

  async function send() {
    const text = draft.trim();
    if (!text || !activeId) return;
    setDraft('');
    try {
      const res = await sendTextMessage(activeId, text);
      setMessages((prev) => [...prev, res.message]);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Не отправилось');
      setDraft(text);
    }
  }

  function onDraftChange(v: string) {
    setDraft(v);
    if (activeId) globalSocket.sendTyping(activeId);
  }

  async function onPickFile(file: File) {
    if (!activeId) return;
    try {
      const res = await uploadMedia(activeId, file);
      setMessages((prev) => [...prev, res.message]);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Ошибка загрузки');
    }
  }

  function logout() {
    setToken(null);
    window.location.reload();
  }

  const filtered = conversations.filter((c) => {
    if (!listFilter.trim()) return true;
    return conversationTitle(c, myId).toLowerCase().includes(listFilter.toLowerCase());
  });

  const activeConv = conversations.find((c) => c.id === activeId);
  const activePeer = activeConv ? peerFromConversation(activeConv, myId) : null;

  if (!getToken()) return null;

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h1>Deep</h1>
          <button type="button" className="btn-icon" title="Новый чат" onClick={() => setShowNewChat(true)}>+</button>
        </div>
        <div className="sidebar-search">
          <input placeholder="Поиск чатов" value={listFilter} onChange={(e) => setListFilter(e.target.value)} />
        </div>
        <div className="chat-list">
          {loadingList ? <p style={{ padding: 16, color: 'var(--muted)' }}>Загрузка…</p> : null}
          {filtered.map((c) => {
            const title = conversationTitle(c, myId);
            const peer = peerFromConversation(c, myId);
            return (
              <button
                key={c.id}
                type="button"
                className={`chat-item ${c.id === activeId ? 'active' : ''}`}
                onClick={() => openChat(c.id, title)}
              >
                <Avatar name={title} online={peer?.online === true} size="sm" />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div className="chat-item-title">{title}</div>
                  <div className="chat-item-preview">{lastMessagePreview(c)}</div>
                </div>
                <div className="chat-item-meta">{formatListTime(c.last_message?.created_at)}</div>
              </button>
            );
          })}
        </div>
        <div style={{ padding: 12, borderTop: '1px solid var(--surface-high)' }}>
          <button type="button" className="btn btn-ghost" style={{ width: '100%' }} onClick={logout}>
            Выйти ({me?.displayName || '…'})
          </button>
        </div>
      </aside>

      <main className="main-panel">
        {!activeId ? (
          <div className="empty-state">Выбери чат или создай новый</div>
        ) : (
          <>
            <header className="chat-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <Avatar name={activeTitle} online={activePeer?.online === true} size="sm" />
                <div>
                  <h2>{activeTitle}</h2>
                  {typing ? <div style={{ fontSize: '0.75rem', color: 'var(--muted)' }}>печатает…</div> : null}
                </div>
              </div>
              <span style={{ fontSize: '0.8rem', color: 'var(--muted)' }}>Звонки — в мобильном приложении</span>
            </header>
            <div className="messages">
              {loadingChat ? <p style={{ color: 'var(--muted)' }}>Загрузка сообщений…</p> : null}
              {messages.map((m) => (
                <MessageBubble key={m.id} msg={m} mine={m.senderId === myId} />
              ))}
              <div ref={messagesEndRef} />
            </div>
            {error ? <div className="typing" style={{ color: 'var(--error)' }}>{error}</div> : null}
            <div className="composer">
              <input
                ref={fileRef}
                type="file"
                hidden
                accept="image/*,video/mp4,audio/*,.pdf,.zip,.txt"
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) onPickFile(f);
                  e.target.value = '';
                }}
              />
              <button type="button" className="btn-icon" title="Вложение" onClick={() => fileRef.current?.click()}>📎</button>
              <textarea
                rows={1}
                placeholder="Сообщение"
                value={draft}
                onChange={(e) => onDraftChange(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    send();
                  }
                }}
              />
              <button type="button" className="btn btn-primary" disabled={!draft.trim()} onClick={send}>
                Отправить
              </button>
            </div>
          </>
        )}
      </main>

      {showNewChat ? (
        <NewChatModal
          onClose={() => setShowNewChat(false)}
          onOpen={(id, title) => {
            loadList();
            openChat(id, title);
          }}
        />
      ) : null}
    </div>
  );
}
