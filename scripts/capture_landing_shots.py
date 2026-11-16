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

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / 'fe' / 'public' / 'landing'

SHOTS = [
    {'name': 'shot-scheduling', 'path': '/app/scheduling', 'nav': '일정 조율'},
    {'name': 'shot-calendar', 'path': '/app/calendar', 'nav': '캘린더'},
    {'name': 'shot-chat', 'path': '/app/chat', 'nav': '채팅'},
    {'name': 'shot-diary', 'path': '/app/diary', 'nav': '일기'},
]

DEMO_LOGIN_TEXT = '데모로 둘러보기'
BOOTING_TEXT = '세션을 복원하고 있어요'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base-url', default='http://localhost:4173')
    ap.add_argument('--width', type=int, default=1280)
    ap.add_argument('--height', type=int, default=800)
    args = ap.parse_args()

    OUT_DIR.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={'width': args.width, 'height': args.height})
        page = context.new_page()

        # 데모 로그인 — snapshot_screens.py와 동일 절차
        page.goto(args.base_url + '/login', wait_until='load')
        page.wait_for_timeout(800)
        page.get_by_text(DEMO_LOGIN_TEXT).click()
        page.wait_for_url('**/app**', timeout=15000)
        # 로그인 직후 refresh 회전이 끝나기 전에 이동·리로드하면 세션이 폐기된다
        page.get_by_text(BOOTING_TEXT).wait_for(state='hidden', timeout=15000)
        page.wait_for_timeout(2500)

        for shot in SHOTS:
            # 인증 화면 이동은 SPA nav 클릭 — page.goto의 풀 리로드는
            # 인메모리 토큰 유실로 auth guard가 랜딩으로 튕긴다
            page.get_by_role('link', name=shot['nav'], exact=True).click()
            page.wait_for_timeout(1500)
            out = OUT_DIR / f"{shot['name']}.jpg"
            page.screenshot(path=str(out), type='jpeg', quality=85)
            print(f"  {shot['name']:16s} {shot['path']:18s} -> {out.relative_to(ROOT)} ({out.stat().st_size // 1024}KB)")

        browser.close()

    print(f"\ndone: {OUT_DIR.relative_to(ROOT)}")


if __name__ == '__main__':
    sys.exit(main())
