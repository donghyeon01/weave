#!/usr/bin/env bash
# weave MySQL 복원 — gzip 덤프를 weave DB로 주입한다. 현재 데이터를 덮어쓴다.
# 사용: ./scripts/restore-mysql.sh <dump.sql.gz> [ENV_FILE]
set -euo pipefail

DUMP="${1:?usage: restore-mysql.sh <dump.sql.gz> [ENV_FILE]}"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${2:-$REPO_DIR/.env.production}"

[ -f "$DUMP" ] || { echo "덤프 파일 없음: $DUMP" >&2; exit 1; }

MYSQL_CID="$(docker ps -q --filter label=com.docker.compose.service=mysql | head -1)"
[ -n "$MYSQL_CID" ] || { echo "mysql 컨테이너가 실행 중이지 않다" >&2; exit 1; }

DB_PASS="${MYSQL_PASSWORD:-}"
if [ -z "$DB_PASS" ] && [ -f "$ENV_FILE" ]; then
  DB_PASS="$(grep -E '^MYSQL_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)"
fi
[ -n "$DB_PASS" ] || { echo "MYSQL_PASSWORD를 찾지 못했다 (env: $ENV_FILE)" >&2; exit 1; }

echo "경고: $DUMP 를 weave DB로 복원한다 — 현재 데이터를 덮어쓴다."
read -r -p "계속할까? [yes/N] " ans
[ "$ans" = "yes" ] || { echo "취소"; exit 1; }

gunzip -c "$DUMP" | docker exec -i -e MYSQL_PWD="$DB_PASS" "$MYSQL_CID" \
  mysql -u weave weave
echo "restore ok: $DUMP"
