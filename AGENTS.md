# Weave — Agent Instructions

Read CONSTRAINTS.md before writing code. Do not weaken it to make a change pass.

- Monorepo: `be/`(Spring Boot 3.4 · Package by Feature `app.weave.{feature}`) + `fe/`(Vite+React 19 · Bulletproof React) + MySQL 8(Flyway).
- 스펙 원천: Obsidian `Project/weave/api-contract.md`·`db-schema.md` — 변경 시 원천 문서와 동시 갱신.
- main/master 직접 커밋 금지. 태스크 브랜치 → 커밋.
- 검증: `npm run check:fast`(변경 시) · `npm run check:full`(완료 전).
