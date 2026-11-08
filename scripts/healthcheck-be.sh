#!/usr/bin/env bash
# weave be 헬스 감시 — /api/health 연속 실패 시 be 컨테이너 재시작 + 로그.
# cron 2분 간격 권장 (install-oci-cron.sh가 설치).
# 환경: WEAVE_HEALTH_URL(기본 http://127.0.0.1:8081/api/health)
#       WEAVE_HEALTH_MAX_FAILS(기본 2 — 연속 실패 이 횟수부터 재시작)
#       WEAVE_OPS_DIR(기본 ~/weave-ops — 로그·상태 파일 위치)
set -uo pipefail

URL="${WEAVE_HEALTH_URL:-http://127.0.0.1:8081/api/health}"
OPS_DIR="${WEAVE_OPS_DIR:-$HOME/weave-ops}"
LOG="$OPS_DIR/health.log"
STATE="$OPS_DIR/health.failcount"
MAX_FAILS="${WEAVE_HEALTH_MAX_FAILS:-2}"

mkdir -p "$OPS_DIR"
fails=0
[ -f "$STATE" ] && fails="$(cat "$STATE")"

if curl -fsS --max-time 10 "$URL" >/dev/null 2>&1; then
  [ "$fails" != "0" ] && echo "$(date -Is) recovered after $fails fails" >> "$LOG"
  echo 0 > "$STATE"
  exit 0
fi

fails=$((fails + 1))
echo "$fails" > "$STATE"
echo "$(date -Is) health fail #$fails ($URL)" >> "$LOG"

if [ "$fails" -ge "$MAX_FAILS" ]; then
  CID="$(docker ps -q --filter label=com.docker.compose.service=be | head -1)"
  if [ -n "$CID" ]; then
    docker restart "$CID" >/dev/null
    echo "$(date -Is) be restarted ($CID) after $fails fails" >> "$LOG"
    echo 0 > "$STATE"
  else
    echo "$(date -Is) be 컨테이너 없음 — 수동 확인 필요" >> "$LOG"
  fi
fi
