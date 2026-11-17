"""랜딩 페이지용 실제 앱 화면 스크린샷 캡처 — fe/public/landing/*.jpg 저장.

용도: 랜딩의 앱 미리보기 섹션에서 쓸 1280×800 viewport JPEG 자산을 확보한다.
인증 절차는 scripts/snapshot_screens.py와 동일 — 하드 리로드 시 인메모리 액세스
토큰이 유실되므로 라우트 간 이동은 반드시 SPA 사이드바 링크 클릭으로 한다.

사용:
    python scripts/capture_landing_shots.py --base-url http://localhost:4173

사전 조건: fe preview + be 서버(demo 로그인 가능)가 기동 중이어야 한다.
"""

import argparse
import sys
from pathlib import Path

from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / 'fe' / 'public' / 'landing'

SHOTS = [
    {'name': 'shot-scheduling', 'path': '/app/scheduling', 'nav': '일정 조율', 'prepare': 'scheduling'},
    {'name': 'shot-calendar', 'path': '/app/calendar', 'nav': '캘린더', 'prepare': 'calendar'},
    {'name': 'shot-chat', 'path': '/app/chat', 'nav': '채팅', 'prepare': 'chat'},
]

DEMO_LOGIN_TEXT = '데모로 둘러보기'
BOOTING_TEXT = '세션을 복원하고 있어요'


def check_friends(page, n=2):
    # input을 label이 감싸고 있어 label 클릭이 더 안정적이다
    labels = page.locator('form li label')
    for i in range(min(n, labels.count())):
        labels.nth(i).click()


def prep_scheduling(page):
    """3단계 위자드 — 친구 2명 선택 후 추천 실행. 실패 시 리셋 후 현재 상태를 캡처한다."""
    try:
        labels = page.locator('form li label')
        # 친구 목록 로딩 중에는 EmptyState('친구가 없어요')만 보인다 — 스켈레톤이
        # 없는 화면이라 체크박스 등장까지 명시적으로 기다린다
        try:
            labels.first.wait_for(timeout=15000)
        except PlaywrightTimeoutError:
            print('    warn: 친구 목록 로딩 타임아웃 — 위자드 초기 상태로 캡처')
            return
        if labels.count() == 0:
            print('    warn: 친구 목록 없음 — 위자드 초기 상태로 캡처')
            return
        check_friends(page)
        page.get_by_role('button', name='AI 추천 슬롯 찾기').click()
        # 추천 결과·빈 결과·폼 에러 모두 종료 상태로 간주한다
        done = (
            page.get_by_text('BEST 추천 슬롯')
            .or_(page.get_by_text('모두 가능한 시간이 없어요'))
            .or_(page.locator('[role="alert"]'))
            .first
        )
        try:
            done.wait_for(timeout=60000)
            has_best = page.get_by_text('BEST 추천 슬롯').count() > 0
            state = '추천 결과' if has_best else '빈 결과/폼 에러'
            print(f'    scheduling: {state} 캡처')
            if has_best:
                # 타임테이블 미리보기는 후보 카드 아래에 렌더되어 1280×800 뷰포트
                # 밖에 있다 — 화면 안으로 스크롤한 뒤 위쪽 여백을 조금 더 확보한다.
                # DATE형 슬롯·멤버 0명 등으로 타임테이블이 없으면 스크롤 없이 둔다
                timetable = page.get_by_text('친구별 일정 겹침 타임테이블 미리보기')
                if timetable.count() > 0:
                    timetable.scroll_into_view_if_needed()
                    page.evaluate('window.scrollBy(0, -80)')
                # 친구 블록·내 일정 쿼리가 비동기로 셀을 채운다 — 스켈레톤이
                # 없어서 고정 대기로 채워질 시간을 준다
                page.wait_for_timeout(1500)
        except PlaywrightTimeoutError:
            # 추천 무응답 — 캘린더→일정 조율로 SPA 왕복해 위자드를 리셋하고,
            # 빈 위자드보다 낫도록 친구 2명을 다시 선택한 상태로 캡처한다
            print('    warn: 추천 결과 타임아웃 — 위자드 리셋 후 친구 선택 상태로 캡처')
            page.get_by_role('link', name='캘린더', exact=True).click()
            page.wait_for_timeout(800)
            page.get_by_role('link', name='일정 조율', exact=True).click()
            # 친구 목록은 스켈레톤 없이 EmptyState로 로딩된다 — 체크박스 등장까지 대기
            try:
                page.locator('form li label').first.wait_for(timeout=15000)
            except PlaywrightTimeoutError:
                pass
            check_friends(page)
    except PlaywrightTimeoutError as e:
        print(f'    warn: scheduling 준비 실패({e}) — 현재 상태로 캡처')


def prep_calendar(page):
    """주간 뷰로 전환 — 탭 클릭 후 그리드 렌더 대기."""
    try:
        page.get_by_role('tablist', name='캘린더 뷰').get_by_role('tab', name='주').click()
        page.wait_for_timeout(800)
    except PlaywrightTimeoutError as e:
        print(f'    warn: calendar 준비 실패({e}) — 현재 상태로 캡처')


def prep_chat(page):
    """메시지가 있는 채팅방 우선 진입 — 전부 비어 있으면 첫 방, 방이 없으면 목록 상태로 캡처한다."""
    try:
        rooms = page.locator('main a[href^="/app/chat/"]')
        if rooms.count() == 0:
            print('    chat: 채팅방 없음 — 목록 상태로 캡처')
            return
        target = rooms.first
        for i in range(rooms.count()):
            candidate = rooms.nth(i)
            if '메시지가 없' not in candidate.inner_text():
                target = candidate
                break
        target.click()
        page.wait_for_timeout(1500)
        print('    chat: 채팅방 상세 캡처')
    except PlaywrightTimeoutError as e:
        print(f'    warn: chat 준비 실패({e}) — 현재 상태로 캡처')


PREPARE = {
    'scheduling': prep_scheduling,
    'calendar': prep_calendar,
    'chat': prep_chat,
}


def main():
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    ap = argparse.ArgumentParser()
    ap.add_argument('--base-url', default='http://localhost:4173')
    ap.add_argument('--width', type=int, default=1280)
    ap.add_argument('--height', type=int, default=800)
    args = ap.parse_args()

    OUT_DIR.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={'width': args.width, 'height': args.height})
        try:
            page = context.new_page()

            # 데모 로그인 — snapshot_screens.py와 동일 절차
            page.goto(args.base_url + '/login', wait_until='load')
            page.wait_for_timeout(800)
            # 랜딩 FAQ 본문에도 같은 문구가 있어 button role로 한정한다
            page.get_by_role('button', name=DEMO_LOGIN_TEXT).click()
            page.wait_for_url('**/app**', timeout=15000)
            # 로그인 직후 refresh 회전이 끝나기 전에 이동·리로드하면 세션이 폐기된다
            page.get_by_text(BOOTING_TEXT).wait_for(state='hidden', timeout=15000)
            page.wait_for_timeout(2500)

            def wait_skeleton():
                # 스켈레톤(role=status)이 사라질 때까지 대기 — be 응답 지연 시
                # 로딩 상태가 마케팅 자산으로 저장되는 것을 막는다. 스켈레톤이
                # 없는 화면은 즉시 통과. websocket(STOMP) 영향 없는 DOM 대기 사용.
                page.wait_for_timeout(600)
                try:
                    page.locator('[role="status"][aria-label="불러오는 중"]').first.wait_for(state='detached', timeout=10000)
                except PlaywrightTimeoutError:
                    pass

            for shot in SHOTS:
                # 인증 화면 이동은 SPA nav 클릭 — page.goto의 풀 리로드는
                # 인메모리 토큰 유실로 auth guard가 랜딩으로 튕긴다
                page.get_by_role('link', name=shot['nav'], exact=True).click()
                wait_skeleton()
                prepare = PREPARE.get(shot.get('prepare'))
                if prepare is not None:
                    prepare(page)
                    wait_skeleton()
                page.wait_for_timeout(400)
                out = OUT_DIR / f"{shot['name']}.jpg"
                page.screenshot(path=str(out), type='jpeg', quality=85)
                print(f"  {shot['name']:16s} {shot['path']:18s} -> {out.relative_to(ROOT)} ({out.stat().st_size // 1024}KB)")
        finally:
            context.close()
            browser.close()

    print(f"\ndone: {OUT_DIR.relative_to(ROOT)}")


if __name__ == '__main__':
    sys.exit(main())
