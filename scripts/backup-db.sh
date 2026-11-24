#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p backups
STAMP="$(date +%Y%m%d_%H%M%S)"
SQLITE_FILE="${SQLITE_PATH:-backend/data/tech-english.db}"
if [[ "${DB_URL:-}" == jdbc:sqlite:* ]]; then
  SQLITE_FILE="${DB_URL#jdbc:sqlite:}"
  SQLITE_FILE="${SQLITE_FILE%%\?*}"
fi

if [ -f "$SQLITE_FILE" ] && [[ "${DB_URL:-}" != jdbc:mysql:* ]]; then
  OUT="backups/tech_english_${STAMP}.db"
  sqlite3 "$SQLITE_FILE" ".backup $OUT"
  echo "SQLite backup written to $OUT"
  exit 0
fi

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-tech_english}"
DB_USERNAME="${MYSQL_USERNAME:-${DB_USERNAME:-tech_english}}"
DB_PASSWORD="${MYSQL_PASSWORD:-${DB_PASSWORD:-}}"
OUT="backups/${DB_NAME}_${STAMP}.sql"

mysqldump --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --password="$DB_PASSWORD" \
  --single-transaction --routines --triggers "$DB_NAME" > "$OUT"

echo "MySQL backup written to $OUT"
