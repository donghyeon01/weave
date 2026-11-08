# Weave 운영 런북 (OCI prod)

OCI 단일 박스 prod의 백업·헬스 감시·복구·재배포 절차.

## 구성

- 박스: OCI Always Free (Ubuntu ARM64), SSH `myOCI` → repo `~/workspaces/weave`
- 컨테이너: `mysql`(mysql:8.0) + `be`(127.0.0.1:8081) — `docker-compose.prod.yml` + `docker-compose.oci.yml`
- 프록시: 호스트 nginx+certbot이 `https://168-107-27-53.nip.io` → `127.0.0.1:8081` (`/ws` 포함)
- fe: Vercel `weave-livid-nine.vercel.app` (이 런북 범위 밖)
- env: `~/workspaces/weave/.env.production` — 권한 600 유지, git 커밋 금지

## 정기 작업 (cron)

`./scripts/install-oci-cron.sh`로 설치 — crontab `# weave-ops` 블록으로 멱등 관리.

| 작업 | 스케줄 | 로그 |
|---|---|---|
| `backup-mysql.sh` | 매일 04:00 Asia/Seoul (`CRON_TZ`) | `~/weave-ops/backup.log` |
| `healthcheck-be.sh` | 2분 간격 | `~/weave-ops/health.log` |

- 백업 산출물: `~/weave-ops/backups/weave-YYYYMMDD-HHMMSS.sql.gz`, 14일 보관 (`WEAVE_BACKUP_KEEP_DAYS`로 조정)
- 헬스 감시: `GET http://127.0.0.1:8081/api/health` 연속 2회 실패 → `docker restart` be. 재시작·복구 이벤트는 health.log에 기록

## 일상 작업

```bash
# 배포 (코드 변경 후)
cd ~/workspaces/weave && git pull
docker compose --env-file .env.production -f docker-compose.prod.yml -f docker-compose.oci.yml up -d --build be

# 상태·로그
docker compose --env-file .env.production -f docker-compose.prod.yml ps
docker logs weave-be-1 --tail 100 -f     # 컨테이너명은 docker ps로 확인
curl -s http://127.0.0.1:8081/api/health
```

## 복구

### mysqldump 복원 (같은 박스)

```bash
./scripts/restore-mysql.sh ~/weave-ops/backups/weave-20261108-040000.sql.gz
# 프롬프트에 yes 입력 — weave DB를 덤프 내용으로 덮어쓴다
```

복원 후 be 재시작으로 커넥션 정리: `docker restart $(docker ps -q --filter label=com.docker.compose.service=be)`

업로드 파일(일기 사진)은 `uploads-*.tar.gz` 별도 덤프 — `tar -xzf ~/weave-ops/backups/uploads-*.tar.gz -C ~/weave-ops/uploads`

### 새 박스로 이전 (박스 전손 시나리오)

1. 사전에 오프박스로 옮겨둔 최신 덤프 확보 (아래 "미적용 항목" 참고 — 미설정이면 백업도 같이 소실)
2. 새 박스에 repo clone + `.env.production` 재작성 (`.env.production.example` 참조)
3. `docker compose --env-file .env.production -f docker-compose.prod.yml -f docker-compose.oci.yml up -d mysql` → healthy 대기
4. `./scripts/restore-mysql.sh <덤프>` → `up -d --build be`
5. nginx+certbot 재구성 후 `curl https://<API_DOMAIN>/api/health` 확인
6. `./scripts/install-oci-cron.sh`로 감시 재설치

## 장애 대응

| 증상 | 확인 | 조치 |
|---|---|---|
| fe에서 API 502 | `docker ps` → be 없음/재시작 반복 | `docker logs <be>` 마지막 100행 — DB 연결 실패면 mysql 먼저 확인 |
| `/api/health` 무응답 | `curl -v http://127.0.0.1:8081/api/health` | be 재시작; 반복 시 health.log에서 재시작 이력 확인 → 로그로 근본 원인 |
| mysql 다운 | `docker ps -a` → exited | `docker start <mysql>` → healthy 후 be 재시작. 볼륨 손상 시 최근 덤프로 복원 |
| 디스크 부족 | `df -h` | `docker system prune` (stopped 컨테이너·빌드 캐시), 오래된 덤프 수동 삭제 |
| 박스 재부팅 후 | `docker ps` | `restart: unless-stopped`로 자동 복귀 설계 — 미기동이면 docker 서비스·compose 순으로 확인 |

### 롤백 (be)

```bash
cd ~/workspaces/weave && git log --oneline -5      # 이전 커밋 해시 확인
git checkout <이전 커밋>                            # 또는 main 복귀 전 핫픽스 브랜치
docker compose --env-file .env.production -f docker-compose.prod.yml -f docker-compose.oci.yml up -d --build be
```

## 미적용 항목 (향후 선택)

- **오프박스 백업** — 덤프가 같은 박스에만 있어 박스 전손 시 데이터 소실. OCI Object Storage + `rclone`(또는 `oci cli`)로 `~/weave-ops/backups` 동기화 cron을 추가하면 해소. 별도 승인 필요.
- **외부 업타임 모니터** — 박스 자체가 죽으면 healthcheck-be.sh도 못 돈다. UptimeRobot 무료 플랜에 `https://168-107-27-53.nip.io/api/health` 5분 감시 등록 권장 (사용자 계정).
