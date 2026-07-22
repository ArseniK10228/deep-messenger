#!/usr/bin/env bash
set -euo pipefail
export PAGER=cat PGPPAGER=cat
DB=postgres://deep:deep@127.0.0.1:5432/deep_messenger

echo "=== USERS ==="
psql "$DB" -c "SELECT username, client_state, client_state_at, app_version_name FROM users WHERE username IN ('hepegg','superant');"

echo "=== CALL RECORDINGS ==="
psql "$DB" -c "SELECT r.call_id, r.started_at, r.ended_at, r.duration_ms, u1.username AS caller, u2.username AS callee
FROM call_recordings r
LEFT JOIN users u1 ON u1.id = r.caller_id
LEFT JOIN users u2 ON u2.id = r.callee_id
ORDER BY r.started_at DESC NULLS LAST LIMIT 10;"

echo "=== CALL API (today) ==="
journalctl -u deep-messenger --no-pager --since "2026-07-22 00:00:00" 2>/dev/null \
  | grep -E 'POST.*/api/v1/calls|/accept|/end' \
  | tail -30
