---
domain: development
type: doc
status: draft
sensitivity: internal
created: 2026-11-04
updated: 2026-11-04
project: weave
distill_level: 0
tags: [project, spec, weave, constraints]
---

# Constraints — 위브 (Weave)

> 이 파일은 레포 생성 시 `weave/CONSTRAINTS.md`로 이동한다. Phase E 착수 시 `weave/AGENTS.md`에 한 줄 추가: `Read CONSTRAINTS.md before writing code. Do not weaken it to make a change pass.`
> 수치 출처: 기획서 §3.2(정량 지표)·§3.3(완료 정의)·§9(비기능)·§10(테스트) + constraint-driven-development sane defaults.

Last reviewed: 2026-11-04 (spec 승인과 동시)

## Floor (항상 강제, 설정 불필요)

- 새 억제 주석 금지: `@ts-ignore`, `eslint-disable`, `@SuppressWarnings`, `// nosemgrep`, `gitleaks:allow`
- 미구현 스텁 금지: `throw new Error("Not implemented")`, 빈 `catch {}`, TODO 상태로 두고 완료 선언
- 이유 없는 테스트 스킵·삭제 금지 — `.skip`/`@Disabled`는 커밋 메시지에 사유 명시
- 시크릿 소스 코드 금지 — `.env`는 커밋 금지, `.env.example`만 유지
- 이 파일을 통과를 위해 약화시키지 않는다 — 예외는 아래 Exceptions 테이블에만 (owner+만료 필수)

## Enforced with numbers

| Dimension | Rule | Checked by | Runs at |
|---|---|---|---|
| Types (fe) | `tsc --noEmit` 에러 0 | tsc | every edit |
| Lint (fe) | 설정 기준 에러 0 | eslint | every edit |
| Compile (be) | `./gradlew compileJava` 통과 | gradle | every edit |
| Secrets | 탐지 0건 — 값은 출력 금지 | `gitleaks detect --redact --no-banner` | every edit, CI |
| Coverage (be) | 변경 라인 ≥ 80% | `./gradlew test jacocoTestReport` + git diff | task end, CI |
| Coverage (fe) | 변경 라인 ≥ 80% | `vitest run --coverage` + git diff | task end, CI |
| 보안: 코드 | high finding 0건 | `semgrep scan --config p/default` | CI |
| 보안: 의존성 | high 이상 0건 | `osv-scanner scan source -r .` | CI |
| 번들 | 랜딩 엔트리 gzip ≤ 150 kB | `size-limit` 또는 vite 빌드 리포트 | task end, CI |
| 접근성 | axe critical/serious 0건 | `axe $PREVIEW_URL --tags wcag2a,wcag2aa,wcag21aa` | preview deploy |
| 런타임 성능 | LCP ≤ 2500ms, CLS ≤ 0.1 | `lighthouse $PREVIEW_URL --output=json --throttling-method=devtools` — 실측 쓰로틀링 적용. 기본 simulate(lantern)는 웹폰트를 LCP 종속으로 과대계상해 한글 폰트 앱에서 구조적 측정 불가 | preview deploy |
| 디자인 토큰 | `fe/src` 내 하드코딩 색상·섀도우 리터럴 0건 (토큰 정의 파일 제외) | grep CI 체크 (`#[0-9a-fA-F]{3,8}` 스캔) | CI |
| 아키텍처 | 도메인 패키지 의존이 capability-map 단방향과 일치 (순환 0) | ArchUnit 테스트 (`be`) | CI |
| API 계약 | `code`·enum·경로가 api-contract.md와 일치 — 파괴 변경은 문서 동시 갱신 | PR 리뷰 + `backend-api-pr-review` 체크리스트 | PR |
| 스키마 관리 | Flyway 마이그레이션 외 스키마 변경 0건 — `ddl-auto=validate` | 설정값 grep (`spring.jpa.hibernate.ddl-auto`) | CI |
| 쿼리 | 나열 핫 쿼리 EXPLAIN에 `type: ALL`(풀스캔)·filesort 없음 | `EXPLAIN` 실측 (mysql-performance) | task end (해당 작업 시) |

핫 쿼리 목록: 일정 range 겹침 조회, 친구 목록(양방향), 알림 목록 페이징, 조율 계산 로드(기간×참여자), 세션 LRU 조회.

### 도메인 완료 정의 (기획서 §3.3 — 통합 테스트로 강제)

| 규칙 | 검증 |
|---|---|
| 동시 401 → refresh 요청 정확히 1회 + 대기 요청 새 토큰 재실행 | fe 인터셉터 단위 테스트 |
| 제안 전원 수락 → `CONFIRMED` + 전원 EVENTS 생성 + 알림, 단일 트랜잭션 | `@SpringBootTest` 통합 테스트 (경합 케이스 포함) |
| 친구·제안 상태 전이가 모든 경로에서 일관 — 불법 전이는 409 | 도메인 단위 + 통합 테스트 |
| 세션 LRU — 4개 초과 시 `last_accessed_at` 최소 행 퇴출, UA 불일치 갱신 거부 | 통합 테스트 |

## Measured, not yet enforced

| Metric | Today | Direction |
|---|---|---|
| 프로젝트 커버리지 (be/fe) | 첫 측정 시 기록 | 하락 금지 (±0.5% 허용) |
| 번들 총량 (전체 엔트리) | 첫 빌드 시 기록 | 증가 금지 |
| 알림 목록 쿼리 시간 | 첫 실측 시 기록 | 악화 금지 |

## Exceptions

| ID | Rule | Path | Reason | Owner | Expires |
|----|------|------|--------|-------|---------|
| DEP-OSV-001 | 보안: 의존성 | `be/gradle.lockfile` (spring-webmvc 6.2.19) | GHSA-j9f9-w8pj-32f8 — SSE+view fragment 스트림 오염. REST JSON 전용이라 SseEmitter·fragment 미사용, 도달 불가. 6.2 백포트·Boot 4 시 재평가 | s_don | 2027-02-07 |
| DEP-OSV-002 | 보안: 의존성 | `be/gradle.lockfile` (spring-webmvc 6.2.19) | GHSA-pc63-qcmh-9cmg — XsltView SSRF/RCE. 뷰 리졸버 미구성·'/**' 뷰 렌더링 없음, 도달 불가. 6.2 백포트·Boot 4 시 재평가 | s_don | 2027-02-07 |

## 체크 스크립트 (레포 생성 후 설치)

| 단계 | 명령 | 내용 | 예산 |
|---|---|---|---|
| `check:fast` | `tsc --noEmit && eslint . && gitleaks detect --redact` + `gradlew compileJava` | 타입·린트·시크릿·컴파일 | < 5s, 변경 파일 |
| `check:task` | `check:fast` + `vitest run --coverage` + `gradlew test jacocoTestReport` | 관련 테스트·커버리지 | < 90s |
| `check:full` | `check:task` + semgrep + osv-scanner + size-limit + ArchUnit | 전체 | CI 무제한 |
| `check:preview` | axe + lighthouse | 실행 URL 필요 | preview deploy |

## 운영 규칙

- 변경 라인 커버리지는 lcov와 `git diff` 교집합 — 테스트를 두 번 돌리지 않는다.
- 바 약화 감시 (PR diff에서 확인): 임계값 하향, `.skip` 추가, 억제 주석 신설, Exceptions 무승인 추가, CONSTRAINTS.md와 기능 변경 동일 커밋.
- 외부 제약 최소 1개 필수 — axe(WCAG)·osv-scanner(취약점 DB)·lighthouse(실브라우저)가 해당.
- 예외는 owner+만료(최대 90일) 없이는 추가 불가.
