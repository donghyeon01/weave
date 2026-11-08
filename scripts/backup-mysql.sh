#!/usr/bin/env bash
# weave MySQL 백업 — mysqldump → gzip → 보관 기간 초과분 삭제.
# 사용: ./scripts/backup-mysql.sh [ENV_FILE]   (기본: <repo>/.env.production)
# 환경: WEAVE_BACKUP_DIR(기본 ~/weave-ops/backups), WEAVE_BACKUP_KEEP_DAYS(기본 14)
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${1:-$REPO_DIR/.env.production}"
BACKUP_DIR="${WEAVE_BACKUP_DIR:-$HOME/weave-ops/backups}"
KEEP_DAYS="${WEAVE_BACKUP_KEEP_DAYS:-14}"

mkdir -p "$BACKUP_DIR"

MYSQL_CID="$(docker ps -q --filter label=com.docker.compose.service=mysql | head -1)"
[ -n "$MYSQL_CID" ] || { echo "mysql 컨테이너가 실행 중이지 않다" >&2; exit 1; }

DB_PASS="${MYSQL_PASSWORD:-}"
if [ -z "$DB_PASS" ] && [ -f "$ENV_FILE" ]; then
  DB_PASS="$(grep -E '^MYSQL_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)"
fi
[ -n "$DB_PASS" ] || { echo "MYSQL_PASSWORD를 찾지 못했다 (env: $ENV_FILE)" >&2; exit 1; }

OUT="$BACKUP_DIR/weave-$(date +%Y%m%d-%H%M%S).sql.gz"
docker exec -e MYSQL_PWD="$DB_PASS" "$MYSQL_CID" \
  mysqldump -u weave --single-transaction --routines --events weave \
  | gzip > "$OUT"

if [ ! -s "$OUT" ]; then
  echo "백업 파일이 비었다: $OUT" >&2
  rm -f "$OUT"
  exit 1
fi

find "$BACKUP_DIR" -name 'weave-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "backup ok: $OUT ($(du -h "$OUT" | cut -f1))"
