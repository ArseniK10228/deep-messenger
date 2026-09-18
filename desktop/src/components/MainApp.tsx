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
import { Paperclip, Phone, Plus, Search, Send, X } from 'lucide-react';
import { useCall } from '../call/CallContext';
import { bindSocketNetworkRecovery, globalSocket } from '../ws/socket';
import { conversationTitle, formatListTime, lastMessagePreview, peerFromConversation } from '../utils/chat';
import { openMessageFile } from '../utils/openMessageFile';
import { appendMessageUnique, normalizeWsMessage } from '../utils/message';
import { Avatar } from './Avatar';
import { BrandLogo } from './BrandLogo';
import { FileViewerModal } from './FileViewerModal';
import { MessageBubble } from './MessageBubble';
import { NewChatModal } from './NewChatModal';
import { TypingIndicator } from './TypingIndicator';

export function MainApp() {
  const { startAudioCall, call: callUi } = useCall();
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
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [fileDragOver, setFileDragOver] = useState(false);
  const [viewerMsg, setViewerMsg] = useState<Message | null>(null);
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
    setPendingFile(null);
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
    bindSocketNetworkRecovery();
    globalSocket.connect();

    const off = globalSocket.onEvent((ev) => {
      const type = ev.type as string;
      if (type === 'message' && ev.message) {
        const msg = normalizeWsMessage(ev.message);
        if (!msg) return;
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
          setMessages((prev) => {
            if (msg.senderId === myId) return prev;
            return appendMessageUnique(prev, msg);
          });
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
      reportClient(true, callUi.phase !== 'idle').catch(() => {});
    }, 30_000);
    reportClient(true, callUi.phase !== 'idle').catch(() => {});

    return () => {
      off();
      clearInterval(heartbeat);
      globalSocket.disconnect();
    };
  }, [activeId, loadList, myId]);

  useEffect(() => {
    const scroll = () => messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    scroll();
    if (typing) {
      const t = window.setTimeout(scroll, 280);
      return () => window.clearTimeout(t);
    }
    return undefined;
  }, [messages, activeId, typing]);

  function queueFile(file: File) {
    if (!activeId) return;
    setPendingFile(file);
    setError(null);
  }

  function attachFromDrag(e: React.DragEvent) {
    e.preventDefault();
    setFileDragOver(false);
    const file = e.dataTransfer.files?.[0];
    if (file) queueFile(file);
  }

  async function send() {
    const text = draft.trim();
    if (!activeId) return;
    if (!text && !pendingFile) return;

    if (pendingFile) {
      const file = pendingFile;
      const caption = text;
      setPendingFile(null);
      setDraft('');
      setUploading(true);
      try {
        const res = await uploadMedia(activeId, file, undefined, false, caption);
        setMessages((prev) => appendMessageUnique(prev, res.message));
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Ошибка загрузки');
        setPendingFile(file);
        setDraft(caption);
      } finally {
        setUploading(false);
      }
      return;
    }

    setDraft('');
    try {
      const res = await sendTextMessage(activeId, text);
      setMessages((prev) => appendMessageUnique(prev, res.message));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Не отправилось');
      setDraft(text);
    }
  }

  function onDraftChange(v: string) {
    setDraft(v);
    if (activeId) globalSocket.sendTyping(activeId);
  }

  function onPickFile(file: File) {
    queueFile(file);
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
        <div className="sidebar-header sidebar-header-row">
          <BrandLogo size={32} />
          <button type="button" className="btn-icon" title="Новый чат" onClick={() => setShowNewChat(true)}>
            <Plus size={22} />
          </button>
        </div>
        <div className="sidebar-search search-wrap">
          <Search size={18} className="search-icon" />
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
        <div className="sidebar-footer">
          {window.deepDesktop?.checkForUpdates ? (
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              style={{ width: '100%', marginBottom: 8 }}
              onClick={() => window.deepDesktop?.checkForUpdates?.()}
            >
              Проверить обновления
            </button>
          ) : null}
          <button type="button" className="btn btn-ghost" style={{ width: '100%' }} onClick={logout}>
            Выйти ({me?.displayName || '…'})
          </button>
        </div>
      </aside>

      <main className="main-panel">
        {!activeId ? (
          <div className="empty-state anim-fade-in">
            <BrandLogo size={96} showText={false} />
            <p>Выбери чат или создай новый</p>
          </div>
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
              <button
                type="button"
                className="btn-icon call-header-btn"
                title="Аудиозвонок"
                disabled={callUi.phase !== 'idle'}
                onClick={() => {
                  if (!activeId) return;
                  startAudioCall(activeId, activeTitle).catch((e) => {
                    setError(e instanceof Error ? e.message : 'Не удалось позвонить');
                  });
                }}
              >
                <Phone size={22} />
              </button>
            </header>
            <div
              className={`messages ${fileDragOver ? 'messages-drag-over' : ''}`}
              onDragEnter={(e) => {
                if (e.dataTransfer.types.includes('Files')) {
                  e.preventDefault();
                  setFileDragOver(true);
                }
              }}
              onDragOver={(e) => {
                if (e.dataTransfer.types.includes('Files')) {
                  e.preventDefault();
                  e.dataTransfer.dropEffect = 'copy';
                }
              }}
              onDragLeave={(e) => {
                if (e.currentTarget === e.target) setFileDragOver(false);
              }}
              onDrop={attachFromDrag}
            >
              {fileDragOver ? (
                <div className="drop-hint anim-fade-in">Отпусти файл — прикрепится к сообщению</div>
              ) : null}
              {loadingChat ? <p style={{ color: 'var(--muted)' }}>Загрузка сообщений…</p> : null}
              {messages.map((m, i) => (
                <div key={m.id} className="msg-anim" style={{ animationDelay: `${Math.min(i * 18, 120)}ms` }}>
                  <MessageBubble
                    msg={m}
                    mine={m.senderId === myId}
                    onOpenMedia={(msg) => {
                      openMessageFile(msg, setViewerMsg).catch((e) => {
                        setError(e instanceof Error ? e.message : 'Не удалось открыть файл');
                      });
                    }}
                  />
                </div>
              ))}
              <TypingIndicator visible={typing} />
              <div ref={messagesEndRef} />
            </div>
            {error ? <div className="typing" style={{ color: 'var(--error)' }}>{error}</div> : null}
            <div
              className="composer"
              onDragEnter={(e) => {
                if (e.dataTransfer.types.includes('Files')) {
                  e.preventDefault();
                  setFileDragOver(true);
                }
              }}
              onDragOver={(e) => {
                if (e.dataTransfer.types.includes('Files')) {
                  e.preventDefault();
                }
              }}
              onDragLeave={(e) => {
                if (e.currentTarget === e.target) setFileDragOver(false);
              }}
              onDrop={attachFromDrag}
            >
              {pendingFile ? (
                <div className="composer-attachment">
                  <Paperclip size={18} />
                  <span className="composer-attachment-name" title={pendingFile.name}>
                    {pendingFile.name}
                  </span>
                  <span className="composer-attachment-size">
                    {pendingFile.size < 1024 * 1024
                      ? `${Math.round(pendingFile.size / 1024)} KB`
                      : `${(pendingFile.size / (1024 * 1024)).toFixed(1)} MB`}
                  </span>
                  <button
                    type="button"
                    className="btn-icon composer-attachment-remove"
                    onClick={() => setPendingFile(null)}
                    aria-label="Убрать файл"
                  >
                    <X size={18} />
                  </button>
                </div>
              ) : null}
              <input
                ref={fileRef}
                type="file"
                hidden
                accept="*/*"
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) onPickFile(f);
                  e.target.value = '';
                }}
              />
              <button type="button" className="btn-icon" title="Вложение" onClick={() => fileRef.current?.click()}>
                <Paperclip size={22} />
              </button>
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
              <button
                type="button"
                className="btn btn-primary btn-send"
                disabled={uploading || (!draft.trim() && !pendingFile)}
                onClick={send}
              >
                <Send size={18} />
                <span>{uploading ? '…' : 'Отправить'}</span>
              </button>
            </div>
          </>
        )}
      </main>

      {viewerMsg ? (
        <FileViewerModal
          messageId={viewerMsg.id}
          fileName={viewerMsg.body?.trim() || (viewerMsg.kind === 'image' ? 'Фото' : 'Файл')}
          kind={viewerMsg.kind}
          onClose={() => setViewerMsg(null)}
        />
      ) : null}

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
