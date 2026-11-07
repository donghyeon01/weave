"""check:preview — preview 빌드(4173)에 대해 axe 접근성 스캔을 실행한다.

CONSTRAINTS.md `check:preview` 단계: axe critical/serious 0건이 통과 기준.
lighthouse(LCP·CLS)는 별도 CLI로 실행한다 — 인증 플로우를 타지 않는 공개 라우트 대상.

사용:
    python scripts/check_preview.py                          # 전 라우트 스캔
    python scripts/check_preview.py --base-url http://localhost:4173

사전 조건: fe preview(`vite preview --port 4173`) + be 서버(demo 로그인 가능) 기동.
결과 JSON은 snapshots/check-preview-<timestamp>.json 에 저장한다 (git 제외 영역).
"""

import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

from playwright.sync_api import sync_playwright

from snapshot_screens import BOOTING_TEXT, DEMO_LOGIN_TEXT, SCREENS

ROOT = Path(__file__).resolve().parent.parent
AXE_PATH = ROOT / 'fe' / 'node_modules' / 'axe-core' / 'axe.min.js'
AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag21aa']
FAIL_IMPACTS = {'critical', 'serious'}


def run_axe(page):
    page.add_script_tag(path=str(AXE_PATH))
    return page.evaluate(
        """(tags) => axe.run(document, {runOnly: {type: 'tag', values: tags}})
            .then(r => ({violations: r.violations.map(v => ({
                id: v.id, impact: v.impact, help: v.help,
                nodes: v.nodes.map(n => ({
                    target: n.target.join(' '),
                    html: n.html.slice(0, 200),
                    summary: (n.any[0] && n.any[0].data) || null,
                })),
                nodeCount: v.nodes.length,
            })), incomplete: r.incomplete.map(v => v.id)}))""",
        AXE_TAGS,
    )


def main():
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    ap = argparse.ArgumentParser()
    ap.add_argument('--base-url', default='http://localhost:4173')
    ap.add_argument('--width', type=int, default=1440)
    ap.add_argument('--height', type=int, default=900)
    ap.add_argument('--dark', action='store_true')
    args = ap.parse_args()

    report = {'run_id': datetime.now().strftime('%Y%m%d-%H%M%S'),
              'base_url': args.base_url, 'tags': AXE_TAGS, 'screens': []}
    authed = False
    failures = 0

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(
            viewport={'width': args.width, 'height': args.height},
            color_scheme='dark' if args.dark else 'light')
        page = context.new_page()
        for screen in SCREENS:
            url = args.base_url + screen['path']
            if screen['auth'] and authed and 'nav' in screen:
                page.get_by_role('link', name=screen['nav'], exact=True).click()
            else:
                page.goto(url, wait_until='load')
            page.wait_for_timeout(1200)
            if screen['auth'] and not authed:
                if '/login' in page.url or page.url.rstrip('/') == args.base_url:
                    page.goto(args.base_url + '/login', wait_until='load')
                    page.wait_for_timeout(800)
                page.get_by_text(DEMO_LOGIN_TEXT).click()
                page.wait_for_url('**/app**', timeout=15000)
                page.get_by_text(BOOTING_TEXT).wait_for(state='hidden', timeout=15000)
                page.wait_for_timeout(2500)
                authed = True
                page.get_by_role('link', name=screen['nav'], exact=True).click()
                page.wait_for_timeout(1200)

            result = run_axe(page)
            blocking = [v for v in result['violations'] if v['impact'] in FAIL_IMPACTS]
            failures += len(blocking)
            report['screens'].append({'name': screen['name'], 'path': screen['path'],
                                      'violations': result['violations'],
                                      'incomplete': result['incomplete'],
                                      'blocking': len(blocking)})
            status = 'FAIL' if blocking else 'ok'
            print(f"  {screen['name']:10s} {status:4s} "
                  f"critical/serious={len(blocking)} total={len(result['violations'])}")
            for v in blocking:
                print(f"    [{v['impact']}] {v['id']}: {v['help']} (x{v['nodeCount']})")
                for n in v['nodes']:
                    print(f"      {n['target']}")
                    print(f"        {n['html']}")
                    if n['summary']:
                        print(f"        {n['summary']}")
        browser.close()

    report['summary'] = {'blocking': failures, 'pass': failures == 0}
    out = ROOT / 'snapshots' / f"check-preview-{report['run_id']}.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f"\nreport: {out.relative_to(ROOT)}")
    print('check:preview axe - ' + ('PASS' if failures == 0 else f'FAIL ({failures} blocking)'))
    return 0 if failures == 0 else 1


if __name__ == '__main__':
    sys.exit(main())
