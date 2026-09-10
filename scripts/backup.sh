#!/usr/bin/env bash
# Exports a full JSON snapshot of the LabNotebook database and commits + pushes
# it to this git repository, so GitHub acts as your off-site cloud backup.
#
# Usage: scripts/backup.sh [API_URL]
#   API_URL defaults to http://127.0.0.1:8000
#
# If LABNOTEBOOK_PASSWORD is set in your environment, it is sent as the
# X-Auth-Token header automatically.

set -euo pipefail

API_URL="${1:-http://127.0.0.1:8000}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="$REPO_ROOT/backups"
mkdir -p "$BACKUP_DIR"

TIMESTAMP="$(date +%Y-%m-%d_%H%M%S)"
OUT_FILE="$BACKUP_DIR/labnotebook-$TIMESTAMP.json"

AUTH_HEADER=()
if [[ -n "${LABNOTEBOOK_PASSWORD:-}" ]]; then
  AUTH_HEADER=(-H "X-Auth-Token: $LABNOTEBOOK_PASSWORD")
fi

echo "Exporting data from $API_URL ..."
curl -sS -f "${AUTH_HEADER[@]}" "$API_URL/api/backup/export" -o "$OUT_FILE"

# Keep only the latest backup checked into git history noise low: also
# maintain a stable "latest.json" pointer.
cp "$OUT_FILE" "$BACKUP_DIR/latest.json"

cd "$REPO_ROOT"
git add "$BACKUP_DIR"
if git diff --cached --quiet; then
  echo "No changes to back up."
  exit 0
fi

git commit -m "Backup: $TIMESTAMP"
git push
echo "Backup committed and pushed: $OUT_FILE"
