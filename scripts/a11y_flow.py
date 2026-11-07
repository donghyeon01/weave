"""키보드 접근성 실측 — Tab 순회·포커스 가시성·모달 트랩·Escape 복귀·뷰포트·모션 환경설정.

사전 조건: fe(5173) + be(8081, DEMO_ENABLED=true) 기동.
결과 JSON은 snapshots/a11y-<timestamp>.json.
"""

import json
import sys
from datetime import datetime
from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent.parent
BASE = 'http://localhost:5173'

FOCUS_JS = """() => {
  const el = document.activeElement
  if (!el || el === document.body) return { tag: 'BODY', visible: null, text: '' }
  const cs = getComputedStyle(el)
  const boxShadow = cs.boxShadow !== 'none'
  const outline = cs.outlineStyle !== 'none' && cs.outlineWidth !== '0px'
  const ring = outline || boxShadow
  return {
    tag: el.tagName,
    text: (el.textContent || el.getAttribute('aria-label') || '').trim().slice(0, 30),
    outline: `${cs.outlineStyle} ${cs.outlineWidth}`,
    boxShadow: cs.boxShadow.slice(0, 60),
    ring,
  }
}"""


def report(mark, name, detail=''):
    print(f"[{mark}] {name}{f' - {detail}' if detail else ''}")
    return mark == 'PASS'


def main():
    results = []
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={'width': 1440, 'height': 900})
        page = context.new_page()

        # 로그인 → /app/todo
        page.goto(BASE + '/login', wait_until='load')
        page.wait_for_timeout(800)
        page.get_by_text('데모로 둘러보기').click()
        page.wait_for_url('**/app**', timeout=15000)
        page.get_by_text('세션을 복원하고 있어요').wait_for(state='hidden', timeout=15000)
        page.wait_for_timeout(1500)

        # ① Tab 순회 — 포커스 가시성(윤곽선·링·섀도우·테두리색 변화) 샘플
        page.get_by_role('link', name='할 일', exact=True).click()
        page.wait_for_timeout(1200)
        invisible, checked = [], 0
        for _ in range(25):
            page.keyboard.press('Tab')
            page.wait_for_timeout(150)
            info = page.evaluate(FOCUS_JS)
            if info['tag'] == 'BODY':
                continue
            checked += 1
            if info['ring']:
                continue
            # outline 미적용 요소는 blur 전후의 테두리색 변화로 포커스 표시를 판정한다
            handle = page.evaluate_handle('() => document.activeElement')
            border_focused = handle.evaluate(
                '(el) => getComputedStyle(el).borderColor')
            handle.evaluate('(el) => el.blur()')
            page.wait_for_timeout(50)
            border_blurred = handle.evaluate(
                '(el) => getComputedStyle(el).borderColor')
            if border_focused == border_blurred:
                invisible.append(info['tag'] + ':' + info['text'])
            handle.evaluate('(el) => el.focus()')  # 순회 위치 복원
            page.wait_for_timeout(50)
        results.append(report('PASS' if not invisible else 'FAIL',
                              f'Tab 순회 포커스 가시성 ({checked}개 검사)',
                              'invisible: ' + ', '.join(invisible[:6]) if invisible else ''))

        # ② 모달 — 포커스 진입·Tab 트랩·Escape 복귀 (키보드 사용자 경로: 포커스→Enter)
        page.keyboard.press('Escape')
        trigger = page.get_by_role('button', name='+ 새 할 일')
        trigger.focus()
        page.keyboard.press('Enter')
        page.wait_for_timeout(500)
        in_modal = page.evaluate(
            "() => !!document.activeElement?.closest('[role=dialog]')")
        results.append(report('PASS' if in_modal else 'FAIL', '모달 오픈 시 포커스 진입'))

        escaped = None
        for _ in range(30):
            page.keyboard.press('Tab')
            page.wait_for_timeout(100)
            outside = page.evaluate(
                "() => !document.activeElement?.closest('[role=dialog]')")
            if outside:
                escaped = page.evaluate(
                    "() => document.activeElement.tagName + ':' + "
                    "(document.activeElement.textContent||'').trim().slice(0,30)")
                break
        results.append(report('PASS' if escaped is None else 'FAIL',
                              '모달 포커스 트랩',
                              f'탈출 → {escaped}' if escaped else 'Tab 30회 내 트랩 유지'))

        page.keyboard.press('Escape')
        page.wait_for_timeout(400)
        back = page.evaluate(
            "() => document.activeElement.tagName + ':' + "
            "(document.activeElement.textContent||'').trim().slice(0,30)")
        results.append(report('PASS' if 'BUTTON' in back or 'A' in back else 'FAIL',
                              'Escape 후 포커스 복귀', back))

        # ③ 320px 뷰포트 — 가로 스크롤 없음
        page.set_viewport_size({'width': 320, 'height': 800})
        page.wait_for_timeout(600)
        overflow = page.evaluate(
            "() => document.documentElement.scrollWidth - document.documentElement.clientWidth")
        results.append(report('PASS' if overflow <= 1 else 'FAIL',
                              '320px 뷰포트 가로 스크롤', f'overflow={overflow}px'))
        page.set_viewport_size({'width': 1440, 'height': 900})

        # ④ prefers-reduced-motion — 전이·애니메이션이 비활성화되는가
        ctx2 = browser.new_context(viewport={'width': 1440, 'height': 900},
                                   reduced_motion='reduce')
        page2 = ctx2.new_page()
        page2.goto(BASE + '/', wait_until='load')
        page2.wait_for_timeout(1000)
        dur = page2.evaluate(
            "() => { const b = document.querySelector('button, a'); "
            "return b ? getComputedStyle(b).transitionDuration : 'n/a' }")
        reduced_ok = dur in ('0s', '0s, 0s', 'n/a') or all(
            float(x.strip()[:-1]) == 0 for x in dur.split(','))
        results.append(report('PASS' if reduced_ok else 'FAIL',
                              'prefers-reduced-motion 적용', f'transitionDuration={dur}'))
        ctx2.close()

        browser.close()

    fails = sum(1 for ok in results if not ok)
    print(f'\n결과: {len(results) - fails}/{len(results)} 통과')
    out = ROOT / 'snapshots' / f"a11y-{datetime.now().strftime('%Y%m%d-%H%M%S')}.json"
    out.write_text(json.dumps({'results': results}, ensure_ascii=False, indent=2),
                   encoding='utf-8')
    return 1 if fails else 0


if __name__ == '__main__':
    sys.exit(main())
