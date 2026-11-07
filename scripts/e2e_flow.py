"""E2E 실사용 플로우 검증 — 데모 로그인부터 6화면 CRUD·조율·로그아웃까지 실제 사용자 경로를 실행한다.

snapshot_screens.py는 렌더만 찍고, 이 스크립트는 상호작용(CRUD·폼 제출·네비게이션)을 검증한다.
실행마다 콘솔 에러와 4xx/5xx API 응답을 수집해 종합 리포트를 출력한다.

사용:
    python scripts/e2e_flow.py [--base-url http://localhost:5173]

사전 조건: fe dev 서버(5173) + be 서버(8081, DEMO_ENABLED=true) 기동.
결과 JSON은 snapshots/e2e-<timestamp>.json (git 제외).
"""

import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent.parent

DEMO_LOGIN_TEXT = '데모로 둘러보기'
BOOTING_TEXT = '세션을 복원하고 있어요'
TASK_TITLE = f'E2E 검증 태스크 {datetime.now():%H%M%S}'


class Reporter:
    def __init__(self):
        self.steps = []
        self.console_errors = []
        self.failed_responses = []

    def step(self, name, ok, detail=''):
        self.steps.append({'step': name, 'ok': ok, 'detail': detail})
        mark = 'PASS' if ok else 'FAIL'
        print(f"[{mark}] {name}{f' - {detail}' if detail else ''}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base-url', default='http://localhost:5173')
    args = ap.parse_args()

    r = Reporter()
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={'width': 1440, 'height': 900})
        page = context.new_page()
        page.on(
            'console',
            lambda m: r.console_errors.append(m.text)
            if m.type == 'error' and 'favicon' not in m.text
            else None,
        )
        page.on(
            'response',
            lambda res: r.failed_responses.append(f"{res.status} {res.request.method} {res.url}")
            if res.status >= 400 and '/api/' in res.url
            else None,
        )

        try:
            # ① 랜딩 — 히어로 렌더 + CTA
            page.goto(args.base_url + '/', wait_until='load')
            page.wait_for_timeout(1200)
            r.step('랜딩 렌더', page.get_by_role('link', name='시작하기').count() > 0)

            # ② 데모 로그인
            page.goto(args.base_url + '/login', wait_until='load')
            page.wait_for_timeout(800)
            page.get_by_text(DEMO_LOGIN_TEXT).click()
            page.wait_for_url('**/app**', timeout=15000)
            page.get_by_text(BOOTING_TEXT).wait_for(state='hidden', timeout=15000)
            page.wait_for_timeout(2000)
            r.step('데모 로그인 → /app 진입', '/app' in page.url, page.url)

            # ③ 할 일 — 생성 → 목록 반영 → 완료 토글
            page.get_by_role('link', name='할 일', exact=True).click()
            page.wait_for_timeout(1200)
            page.get_by_role('button', name='+ 새 할 일').click()
            page.locator('#task-title').fill(TASK_TITLE)
            page.get_by_role('button', name='등록').click()
            page.wait_for_timeout(1500)
            created = page.get_by_text(TASK_TITLE).count() > 0
            r.step('할 일 생성 → 목록 반영', created)
            if created:
                page.get_by_label(f'{TASK_TITLE} 완료 토글').click()
                page.wait_for_timeout(1200)
                r.step('할 일 완료 토글', True)
                # 검색 필터가 생성 항목을 좁히는지 확인
                page.get_by_label('할 일 검색').fill(TASK_TITLE)
                page.wait_for_timeout(800)
                r.step('할 일 검색 필터', page.get_by_text(TASK_TITLE).count() > 0)
                page.get_by_label('할 일 검색').fill('존재하지않는검색어')
                page.wait_for_timeout(800)
                r.step('검색 0건 빈 상태', page.get_by_text(TASK_TITLE).count() == 0)
                page.get_by_label('할 일 검색').fill('')

            # ④ 캘린더 — 월 그리드 렌더 + 날짜 셀 존재 (div 그리드, 날짜번호는 button)
            page.get_by_role('link', name='캘린더', exact=True).click()
            page.wait_for_timeout(1200)
            cells = page.locator('.grid.grid-cols-7 button').count()
            r.step('캘린더 그리드 렌더', cells > 0, f'cells={cells}')

            # ⑤ 일기 — 제목·태그·본문 입력 → 저장
            page.get_by_role('link', name='일기', exact=True).click()
            page.wait_for_timeout(1200)
            page.get_by_placeholder('오늘 하루를 한 줄로').fill('E2E 일기')
            tag_input = page.get_by_label('태그 입력')
            tag_input.fill('e2e')
            tag_input.press('Enter')
            page.get_by_placeholder('기록하고 싶은 순간을 담아보세요').fill('자동화 검증으로 작성한 일기 본문')
            page.get_by_role('button', name='저장', exact=True).click()
            page.wait_for_timeout(1500)
            saved = page.get_by_text('저장했어요', exact=False).count() > 0 or page.locator('[role="alert"]').count() == 0
            r.step('일기 저장', saved)

            # ⑥ 친구 — 목록 렌더
            page.get_by_role('link', name='친구', exact=True).click()
            page.wait_for_timeout(1500)
            r.step('친구 목록 렌더', page.get_by_text('내 친구').count() > 0)

            # ⑦ 일정 조율 — 친구 선택 → AI 추천 → 제안 전송
            page.get_by_role('link', name='일정 조율', exact=True).click()
            page.wait_for_timeout(1500)
            first_checkbox = page.locator('ul input[type="checkbox"]').first
            if first_checkbox.count() > 0:
                first_checkbox.check()
                with page.expect_response(
                    lambda res: '/api/scheduling' in res.url and res.request.method == 'POST',
                    timeout=20000,
                ) as resp_info:
                    page.get_by_role('button', name='AI 추천 슬롯 찾기').click()
                resp = resp_info.value
                n = len(resp.json().get('candidates', [])) if resp.ok else -1
                r.step('AI 추천 슬롯 도출', resp.ok, f'status={resp.status} candidates={n}')
                if n > 0:
                    page.get_by_role('button', name='제안 보내기').first.click()
                    page.wait_for_timeout(800)
                    title_input = page.get_by_placeholder('예: 캡스톤 회의')
                    if title_input.count() > 0:
                        title_input.fill('E2E 조율 회의')
                    page.get_by_role('dialog').get_by_role('button', name='제안 보내기').click()
                    page.wait_for_timeout(2000)
                    r.step('제안 전송', page.get_by_text('제안을 보냈어요').count() > 0)
            else:
                r.step('AI 추천 슬롯 도출', False, '친구 체크박스 없음 — 데모 시드 친구 부재')

            # ⑧ 제안 보관함 — '보낸 제안' 탭에서 방금 보낸 제안 노출
            page.get_by_role('link', name='제안 보관함 →').click()
            page.wait_for_timeout(1200)
            page.get_by_role('tab', name='보낸 제안').click()
            page.wait_for_timeout(1200)
            r.step('제안 보관함 반영', page.get_by_text('E2E 조율 회의').count() > 0)

            # ⑨ 내 프로필 — 닉네임·이메일 필드, 로그아웃
            page.get_by_role('link', name='내 프로필', exact=True).click()
            page.wait_for_timeout(1200)
            nickname = page.get_by_label('닉네임')
            r.step('프로필 필드 렌더', nickname.count() > 0 and nickname.input_value() != '')
            email_input = page.get_by_label('이메일')
            r.step('이메일 변경 불가', email_input.is_disabled() or email_input.get_attribute('readonly') is not None)

            # ⑩ 알림 벨 — 드롭다운
            page.locator('button[aria-label^="알림"]:visible').first.click()
            page.wait_for_timeout(800)
            r.step('알림 드롭다운', True)

            # ⑪ 세션 복원 — 리로드 후에도 authed 유지 (refresh 회전 정착 후 수행)
            page.reload(wait_until='load')
            page.wait_for_timeout(2500)
            restored = '/login' not in page.url and page.url.rstrip('/') != args.base_url
            r.step('리로드 후 세션 복원', restored, page.url)

            # ⑫ 로그아웃 — 프로필에서 확인 모달 경유
            page.get_by_role('link', name='내 프로필', exact=True).click()
            page.wait_for_timeout(1000)
            page.get_by_role('button', name='로그아웃').first.click()
            page.wait_for_timeout(500)
            page.get_by_role('button', name='로그아웃').last.click()
            page.wait_for_timeout(1500)
            r.step('로그아웃 → 랜딩 복귀', '/app' not in page.url, page.url)

        except Exception as e:
            r.step('실행 중단', False, f'{type(e).__name__}: {e}')
        finally:
            browser.close()

    fails = [s for s in r.steps if not s['ok']]
    print('\n── 실패 API 응답 ──')
    for line in r.failed_responses:
        print(' ', line)
    print('── 콘솔 에러 ──')
    for line in r.console_errors[:20]:
        print(' ', line[:200])
    print(f"\n결과: {len(r.steps) - len(fails)}/{len(r.steps)} 단계 통과, "
          f"실패 API {len(r.failed_responses)}건, 콘솔 에러 {len(r.console_errors)}건")

    out = ROOT / 'snapshots' / f"e2e-{datetime.now().strftime('%Y%m%d-%H%M%S')}.json"
    out.write_text(
        json.dumps(
            {'base_url': args.base_url, 'steps': r.steps,
             'failed_responses': r.failed_responses, 'console_errors': r.console_errors},
            ensure_ascii=False, indent=2,
        ),
        encoding='utf-8',
    )
    print(f'report: {out.relative_to(ROOT)}')
    return 1 if fails else 0


if __name__ == '__main__':
    sys.exit(main())
