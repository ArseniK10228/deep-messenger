#!/usr/bin/env bash
# Publish app-release.json to home API only after APK is live on VPS.
set -euo pipefail

REPO="${REPO:-$(cd "$(dirname "$0")/../.." && pwd)}"
RELEASE_JSON="${RELEASE_JSON:-$REPO/server/app-release.json}"
APK_URL="${APK_URL:-https://deepdesignpc.online/deep.apk}"
API_URL="${API_URL:-https://api.deepdesignpc.online/api/v1/app/release}"
HOME_HOST="${HOME_HOST:-100.118.211.24}"
HOME_USER="${HOME_USER:-root}"
HOME_KEY="${HOME_KEY:-/root/.ssh/home_deploy}"
HOME_RELEASE_PATH="${HOME_RELEASE_PATH:-/opt/deep-messenger/server/app-release.json}"
MIN_APK_BYTES="${MIN_APK_BYTES:-500000}"

if [[ ! -f "$RELEASE_JSON" ]]; then
  echo "ERROR: $RELEASE_JSON not found"
  exit 1
fi

VERSION_CODE=$(node -e "const j=JSON.parse(require('fs').readFileSync('$RELEASE_JSON','utf8')); if(!j.versionCode||!j.versionName){process.exit(1)}; console.log(j.versionCode)")
VERSION_NAME=$(node -e "const j=JSON.parse(require('fs').readFileSync('$RELEASE_JSON','utf8')); if(!j.versionCode||!j.versionName){process.exit(1)}; console.log(j.versionName)")

echo "==> Verify APK is downloadable: $APK_URL"
apk_ok=0
for i in $(seq 1 12); do
  SIZE=$(curl -fsSIL --max-time 20 "$APK_URL" | awk 'tolower($1)=="content-length:" {print $2}' | tr -d '\r' | tail -1)
  if [[ -n "$SIZE" && "$SIZE" -ge "$MIN_APK_BYTES" ]]; then
    echo "APK OK: ${SIZE} bytes"
    apk_ok=1
    break
  fi
  echo "waiting for APK ($i/12)..."
  sleep 10
done
if [[ "$apk_ok" -ne 1 ]]; then
  echo "ERROR: APK not available or too small at $APK_URL"
  exit 1
fi

echo "==> Upload app-release.json to home ($VERSION_NAME / $VERSION_CODE)"
HOME_OPTS=(-o BatchMode=yes -o StrictHostKeyChecking=no -o ConnectTimeout=30)
if [[ -n "${SSH_KEY_FILE:-}" && -f "${SSH_KEY_FILE}" ]]; then
  # GitHub Actions runner → VPS → home
  VPS_HOST="${VPS_HOST:?VPS_HOST required}"
  VPS_USER="${VPS_USER:?VPS_USER required}"
  VPS_OPTS=(-i "$SSH_KEY_FILE" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=30)
  scp "${VPS_OPTS[@]}" "$RELEASE_JSON" "${VPS_USER}@${VPS_HOST}:/tmp/deep-app-release.json"
  ssh "${VPS_OPTS[@]}" "${VPS_USER}@${VPS_HOST}" \
    "scp -i '$HOME_KEY' ${HOME_OPTS[*]} /tmp/deep-app-release.json ${HOME_USER}@${HOME_HOST}:${HOME_RELEASE_PATH}"
else
  # VPS-local invocation
  scp -i "$HOME_KEY" "${HOME_OPTS[@]}" "$RELEASE_JSON" "${HOME_USER}@${HOME_HOST}:${HOME_RELEASE_PATH}"
fi

echo "==> Verify API exposes published version"
api_ok=0
for i in $(seq 1 12); do
  LIVE_CODE=$(curl -fsS --max-time 15 "$API_URL" | node -e "let d='';process.stdin.on('data',c=>d+=c);process.stdin.on('end',()=>console.log(JSON.parse(d).versionCode))")
  LIVE_NAME=$(curl -fsS --max-time 15 "$API_URL" | node -e "let d='';process.stdin.on('data',c=>d+=c);process.stdin.on('end',()=>console.log(JSON.parse(d).versionName))")
  if [[ "$LIVE_CODE" == "$VERSION_CODE" && "$LIVE_NAME" == "$VERSION_NAME" ]]; then
    echo "API OK: versionCode=$LIVE_CODE versionName=$LIVE_NAME"
    api_ok=1
    break
  fi
  echo "waiting for API release metadata ($i/12): live=$LIVE_CODE/$LIVE_NAME expected=$VERSION_CODE/$VERSION_NAME"
  sleep 5
done
if [[ "$api_ok" -ne 1 ]]; then
  echo "ERROR: API did not publish expected release metadata"
  exit 1
fi

echo "Release $VERSION_NAME ($VERSION_CODE) published"
