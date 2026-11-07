"""화면 스냅샷 캡처 — 각 라우트의 스크린샷 + 소스·컴포넌트 manifest를 snapshots/에 저장한다.

용도: 현재 화면 상태를 임시 보존해 Figma 재설계·회귀 비교의 기준점으로 쓴다.
snapshots/ 는 .gitignore 처리되어 로컬 전용이다.

사용:
    python scripts/snapshot_screens.py                     # snapshots/<timestamp>/ 생성
    python scripts/snapshot_screens.py --base-url http://localhost:4173
    python scripts/snapshot_screens.py --tag before-redesign

사전 조건: fe dev/preview 서버 + be 서버(demo 로그인 가능)가 기동 중이어야 한다.
"""

import argparse
import json
import subprocess
import sys
from datetime import datetime
from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent.parent
FE = ROOT / 'fe'

# 라우트 → 소스 매핑. 새 화면 추가 시 여기에 1행 추가.
SCREENS = [
    {'name': 'landing', 'path': '/', 'auth': False, 'route_file': 'src/app/routes/landing.tsx', 'feature': 'landing'},
    {'name': 'login', 'path': '/login', 'auth': False, 'route_file': 'src/app/routes/login.tsx', 'feature': 'auth'},
    {'name': 'todo', 'path': '/app/todo', 'auth': True, 'route_file': 'src/app/routes/app/todo.tsx', 'feature': 'tasks', 'nav': '할 일'},
    {'name': 'calendar', 'path': '/app/calendar', 'auth': True, 'route_file': 'src/app/routes/app/calendar.tsx', 'feature': 'events', 'nav': '캘린더'},
    {'name': 'diary', 'path': '/app/diary', 'auth': True, 'route_file': 'src/app/routes/app/diary.tsx', 'feature': 'diary', 'nav': '일기'},
    {'name': 'friends', 'path': '/app/friends', 'auth': True, 'route_file': 'src/app/routes/app/friends.tsx', 'feature': 'friends', 'nav': '친구'},
    {'name': 'scheduling', 'path': '/app/scheduling', 'auth': True, 'route_file': 'src/app/routes/app/scheduling.tsx', 'feature': 'scheduling', 'nav': '일정 조율'},
    {'name': 'profile', 'path': '/app/profile', 'auth': True, 'route_file': 'src/app/routes/app/profile.tsx', 'feature': 'profile', 'nav': '내 프로필'},
    {'name': 'proposals', 'path': '/app/proposals', 'auth': True, 'route_file': 'src/app/routes/app/proposals.tsx', 'feature': 'scheduling'},
]

SHARED_DIRS = ['src/components/ui', 'src/components/layout', 'src/styles']
DEMO_LOGIN_TEXT = '데모로 둘러보기'
BOOTING_TEXT = '세션을 복원하고 있어요'


def git(args):
    return subprocess.run(['git', *args], cwd=ROOT, capture_output=True, text=True).stdout.strip()


def list_sources(screen):
    files = [screen['route_file']]
    feature_dir = FE / 'src' / 'features' / screen['feature']
    if feature_dir.exists():
        files += sorted(str(p.relative_to(FE)) for p in feature_dir.rglob('*.ts*'))
    return files


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base-url', default='http://localhost:5173')
    ap.add_argument('--tag', default=None, help='실행 폴더명에 붙일 라벨 (예: before-redesign)')
    ap.add_argument('--width', type=int, default=1440)
    ap.add_argument('--height', type=int, default=900)
    ap.add_argument('--dark', action='store_true', help='prefers-color-scheme: dark 컨텍스트로 캡처')
    args = ap.parse_args()

    run_id = datetime.now().strftime('%Y%m%d-%H%M%S') + (f'-{args.tag}' if args.tag else '')
    out_dir = ROOT / 'snapshots' / run_id
    screens_dir = out_dir / 'screens'
    screens_dir.mkdir(parents=True, exist_ok=True)

    manifest = {
        'run_id': run_id,
        'captured_at': datetime.now().isoformat(timespec='seconds'),
        'base_url': args.base_url,
        'viewport': {'width': args.width, 'height': args.height},
        'git': {'hash': git(['rev-parse', '--short', 'HEAD']), 'branch': git(['branch', '--show-current']),
                'dirty': bool(git(['status', '--porcelain']))},
        'shared_sources': SHARED_DIRS,
        'screens': [],
    }

    authed = False
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(
            viewport={'width': args.width, 'height': args.height},
            color_scheme='dark' if args.dark else 'light',
        )
        page = context.new_page()
        for screen in SCREENS:
            url = args.base_url + screen['path']
            # 외부 폰트 CDN·WS 연결로 networkidle이 안정되지 않아 load+정착 대기로 잡는다
            if screen['auth'] and authed and 'nav' in screen:
                # 인증 화면 이동은 SPA nav 클릭 — 하드 리로드는 refresh 회전 경합으로
                # 세션을 폐기시킬 수 있다 (구 토큰 중복 전송 → REUSED 판정)
                page.get_by_role('link', name=screen['nav'], exact=True).click()
            else:
                page.goto(url, wait_until='load')
            page.wait_for_timeout(1200)
            if screen['auth'] and not authed:
                # 보호 라우트가 guest를 튕기면 데모 로그인으로 세션을 얻고 재진입한다
                if '/login' in page.url or page.url.rstrip('/') == args.base_url:
                    page.goto(args.base_url + '/login', wait_until='load')
                    page.wait_for_timeout(800)
                page.get_by_text(DEMO_LOGIN_TEXT).click()
                page.wait_for_url('**/app**', timeout=15000)
                # 로그인 직후 STOMP의 ensureFreshToken이 refresh를 회전 중 —
                # 끝나기 전에 리로드하면 구 토큰이 중복 전송돼 세션이 폐기된다
                page.get_by_text(BOOTING_TEXT).wait_for(state='hidden', timeout=15000)
                page.wait_for_timeout(2500)
                authed = True
                page.get_by_role('link', name=screen['nav'], exact=True).click()
                page.wait_for_timeout(1200)
            shot = screens_dir / f"{len(manifest['screens']) + 1:02d}-{screen['name']}.png"
            page.screenshot(path=str(shot), full_page=True)
            manifest['screens'].append({
                'name': screen['name'], 'path': screen['path'],
                'final_url': page.url.replace(args.base_url, ''),
                'screenshot': f"screens/{shot.name}",
                'sources': list_sources(screen),
            })
            print(f"  {screen['name']:10s} {screen['path']:18s} -> {shot.name}")
        browser.close()

    (out_dir / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f"\nsnapshot saved: {out_dir.relative_to(ROOT)}")


if __name__ == '__main__':
    sys.exit(main())
