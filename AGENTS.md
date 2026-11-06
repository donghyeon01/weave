# Weave — Agent Instructions

Read CONSTRAINTS.md before writing code. Do not weaken it to make a change pass.

- Monorepo: `be/`(Spring Boot 3.4 · Package by Feature `app.weave.{feature}`) + `fe/`(Vite+React 19 · Bulletproof React) + MySQL 8(Flyway).
- 스펙 원천: Obsidian `Project/weave/api-contract.md`·`db-schema.md` — 변경 시 원천 문서와 동시 갱신.
- 시각 원천: Figma `6Qqsp5vMaiCThYQxhkUEFS` 8화면 프레임 + `States & Dark` 페이지 (프레임 ID·이탈 원장은 design-system `docs/SYSTEM.md` §시각 계약). 화면 UI 작업 전 `get_design_context`로 시안을 읽는다. 시안과 다르면 이탈 원장에 기록하거나 결함으로 고친다.
- 화면 스냅샷: `python scripts/snapshot_screens.py [--tag 이름]` — `snapshots/`(git 제외)에 라우트별 PNG + 코드/컴포넌트 manifest. fe 5173 + be 8081 기동 필요.
- main/master 직접 커밋 금지. 태스크 브랜치 → 커밋.
- 검증: `npm run check:fast`(변경 시) · `npm run check:full`(완료 전).
