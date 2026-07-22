import { query } from './client.js';
import { getConversationPeer } from './conversations.js';
import { getUserById, type UserRow } from './users.js';

export interface CallRecordingRow {
  id: string;
  call_id: string;
  conversation_id: string | null;
  caller_id: string | null;
  callee_id: string | null;
  started_at: string;
  ended_at: string | null;
  duration_ms: number | null;
  media_path: string | null;
  media_mime: string | null;
  media_size: string | null;
  video: boolean;
  uploaded_by: string | null;
  created_at: string;
}

function userLabel(user: UserRow | null | undefined): string | null {
  if (!user) return null;
  const name = user.display_name?.trim();
  if (name) return name;
  if (user.username) return `@${user.username}`;
  if (user.email) return user.email;
  if (user.phone) return user.phone;
  return user.id.slice(0, 8);
}

export function mapCallRecording(
  row: CallRecordingRow,
  users?: {
    caller?: UserRow | null;
    callee?: UserRow | null;
    uploader?: UserRow | null;
  }
) {
  const callerName = userLabel(users?.caller);
  const calleeName = userLabel(users?.callee);
  const uploadedByName = userLabel(users?.uploader);
  return {
    id: row.id,
    callId: row.call_id,
    conversationId: row.conversation_id,
    callerId: row.caller_id,
    calleeId: row.callee_id,
    callerName,
    calleeName,
    callerUsername: users?.caller?.username ?? null,
    calleeUsername: users?.callee?.username ?? null,
    uploadedByName,
    title: buildRecordingTitle(callerName, calleeName, uploadedByName),
    startedAt: row.started_at,
    endedAt: row.ended_at,
    durationMs: row.duration_ms,
    mediaUrl: row.media_path ? `/media/${row.media_path}` : null,
    mediaMime: row.media_mime,
    mediaSize: row.media_size ? Number(row.media_size) : null,
    video: row.video,
    uploadedBy: row.uploaded_by,
    createdAt: row.created_at
  };
}

export function buildRecordingTitle(
  callerName: string | null,
  calleeName: string | null,
  uploadedByName?: string | null
): string {
  if (callerName && calleeName) return `${callerName} → ${calleeName}`;
  if (callerName) return `Звонок: ${callerName}`;
  if (calleeName) return `Звонок: ${calleeName}`;
  if (uploadedByName) return `Запись от ${uploadedByName}`;
  return 'Звонок';
}

async function resolveRecordingParties(row: CallRecordingRow): Promise<{
  callerId: string | null;
  calleeId: string | null;
}> {
  let callerId = row.caller_id;
  let calleeId = row.callee_id;
  if ((!callerId || !calleeId) && row.conversation_id && row.uploaded_by) {
    const peer = await getConversationPeer(row.uploaded_by, row.conversation_id);
    if (peer) {
      callerId = callerId ?? row.uploaded_by;
      calleeId = calleeId ?? peer.id;
    }
  }
  return { callerId, calleeId };
}

export async function enrichCallRecording(row: CallRecordingRow) {
  const { callerId, calleeId } = await resolveRecordingParties(row);
  const [caller, callee, uploader] = await Promise.all([
    callerId ? getUserById(callerId) : null,
    calleeId ? getUserById(calleeId) : null,
    row.uploaded_by ? getUserById(row.uploaded_by) : null
  ]);
  return mapCallRecording(row, { caller, callee, uploader });
}

export async function insertCallRecording(input: {
  callId: string;
  conversationId?: string | null;
  callerId?: string | null;
  calleeId?: string | null;
  startedAt?: string;
  endedAt?: string;
  durationMs?: number | null;
  mediaPath: string;
  mediaMime: string;
  mediaSize: number;
  video?: boolean;
  uploadedBy: string;
}): Promise<CallRecordingRow> {
  const r = await query<CallRecordingRow>(
    `INSERT INTO call_recordings (
       call_id, conversation_id, caller_id, callee_id,
       started_at, ended_at, duration_ms,
       media_path, media_mime, media_size, video, uploaded_by
     ) VALUES ($1,$2,$3,$4,COALESCE($5::timestamptz, now()),$6::timestamptz,$7,$8,$9,$10,$11,$12)
     RETURNING *`,
    [
      input.callId,
      input.conversationId ?? null,
      input.callerId ?? null,
      input.calleeId ?? null,
      input.startedAt ?? null,
      input.endedAt ?? null,
      input.durationMs ?? null,
      input.mediaPath,
      input.mediaMime,
      input.mediaSize,
      input.video ?? false,
      input.uploadedBy
    ]
  );
  return r.rows[0];
}

export async function listCallRecordings(limit = 100): Promise<CallRecordingRow[]> {
  const r = await query<CallRecordingRow>(
    `SELECT * FROM call_recordings ORDER BY started_at DESC LIMIT $1`,
    [limit]
  );
  return r.rows;
}

export async function listCallRecordingsEnriched(limit = 100) {
  const rows = await listCallRecordings(limit);
  return Promise.all(rows.map((row) => enrichCallRecording(row)));
}

export async function getCallRecording(id: string): Promise<CallRecordingRow | null> {
  const r = await query<CallRecordingRow>('SELECT * FROM call_recordings WHERE id = $1', [id]);
  return r.rows[0] || null;
}
