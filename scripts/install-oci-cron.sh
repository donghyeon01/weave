#!/usr/bin/env bash
# OCI 박스 crontab 멱등 설치 — weave-ops 마커 블록을 제거 후 재삽입한다.
#   백업: 매일 04:00 Asia/Seoul → ~/weave-ops/backup.log
#   헬스: 2분 간격 → ~/weave-ops/health.log
# 사용: ./scripts/install-oci-cron.sh
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

current="$(crontab -l 2>/dev/null || true)"
cleaned="$(printf '%s\n' "$current" | sed '/# weave-ops begin/,/# weave-ops end/d')"

cat <<EOF | { printf '%s\n' "$cleaned" | grep -v '^$' || true; cat; } | crontab -
# weave-ops begin
CRON_TZ=Asia/Seoul
0 4 * * * $REPO_DIR/scripts/backup-mysql.sh >> \$HOME/weave-ops/backup.log 2>&1
*/2 * * * * $REPO_DIR/scripts/healthcheck-be.sh
# weave-ops end
EOF

crontab -l | sed -n '/# weave-ops begin/,/# weave-ops end/p'
