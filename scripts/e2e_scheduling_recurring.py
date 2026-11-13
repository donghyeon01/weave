"""E2E 정례(반복) 조율 플로우 검증 — V2 scheduling extension.

API 셋업(범위·선호 시간대) 후 실제 브라우저로 scheduling 화면을 구동한다:
요일 칩 → 정례 추천(시리즈 카드·회차 펼침·선호 적합도) → 제안 모달(정기 일정 요약)
→ 제안 전송 → 보관함 시리즈 배지 → 채팅방 "총 N회" 배지까지 확인한다.

수락→확정→EVENTS 생성·PROPOSAL-CONFLICT는 제2 계정이 필요 — 데모 로그인은
단일 사용자라 API 수준에서만 검증 가능하며, 본 스크립트는 단일 계정 UI 경로를 커버한다.

사용:
    python scripts/e2e_scheduling_recurring.py [--base-url http://localhost:4173]

사전 조건: fe dev 서버 + be 서버(8081, DEMO_ENABLED=true) + mysql 기동.
"""

import argparse
import json
import sys
import uuid
from pathlib import Path

import requests
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent.parent
API = 'http://localhost:8081'

# 정례 조건 — 월·수 4회, 범위 11/23~12/02 안에 정확히 4회가 들어간다
RANGE_START = '2026-11-23'
RANGE_END = '2026-12-02'
BYDAY = [1, 3]  # ISO 요일 월·수
COUNT = 4
SLOT_DATE = '2026-11-23'


class Reporter:
    def __init__(self):
        self.steps = []
        self.console_errors = []
        self.failed_responses = []

    def step(self, name, ok, detail=''):
        self.steps.append({'step': name, 'ok': ok, 'detail': detail})
        mark = 'PASS' if ok else 'FAIL'
        print(f"[{mark}] {name}{f' - {detail}' if detail else ''}")


def api(token, method, path, body=None):
    res = requests.request(
        method,
        f'{API}{path}',
        headers={'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'},
        json=body,
        timeout=15,
    )
    return res


def ensure_availability(token):
    """추천 후보가 나올 수 있도록 저녁 시간대 COMPLETED 범위를 준비한다 (idempotent)."""
    scopes = api(token, 'GET', '/api/availability/scopes').json()
    for s in scopes:
        if (
            s['mode'] == 'TIME'
            and s['status'] == 'COMPLETED'
            and s['rangeStart'] <= RANGE_START
            and s['rangeEnd'] >= RANGE_END
        ):
            return s['id']
    created = api(
        token,
        'POST',
        '/api/availability/scopes',
        {'mode': 'TIME', 'rangeStart': '2026-11-16', 'rangeEnd': '2026-12-06'},
    )
    created.raise_for_status()
    sid = created.json()['id']
    put = api(
        token,
        'PUT',
        f'/api/availability/scopes/{sid}/blocks',
        {
            'blocks': [
                {
                    'kind': 'TIME',
                    'startAt': '2026-11-16T18:00:00',
                    'endAt': '2026-12-07T00:00:00',
                }
            ],
            'expectedRevision': 0,
            'status': 'COMPLETED',
        },
    )
    put.raise_for_status()
    return sid


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base-url', default='http://localhost:4173')
    args = ap.parse_args()

    r = Reporter()

    # ① API 셋업 — 데모 로그인 + 선호 시간대(월·수 19~21시) + 가용 범위
    login = requests.post(
        f'{API}/api/auth/demo',
        headers={'User-Agent': 'e2e-scheduling-recurring'},
        timeout=15,
    )
    login.raise_for_status()
    token = login.json()['accessToken']

    pw = api(
        token,
        'PUT',
        '/api/availability/preferred-windows',
        {
            'windows': [
                {'dayOfWeek': d, 'startTime': '19:00', 'endTime': '21:00'} for d in BYDAY
            ]
        },
    )
    pw.raise_for_status()
    r.step('선호 시간대 PUT (월·수 19~21시)', True)

    scope_id = ensure_availability(token)
    r.step('가용 범위 COMPLETED 확보', True, f'scope={scope_id}')

    # ② 브라우저 — 데모 로그인 → 스케줄링 화면
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
            lambda res: r.failed_responses.append(
                f"{res.status} {res.request.method} {res.url}"
            )
            if res.status >= 400 and '/api/' in res.url
            else None,
        )

        page.goto(args.base_url + '/login', wait_until='load')
        page.wait_for_timeout(800)
        page.get_by_text('데모로 둘러보기').click()
        page.wait_for_url('**/app**', timeout=15000)
        page.get_by_text('세션을 복원하고 있어요').wait_for(state='hidden', timeout=15000)
        page.wait_for_timeout(1500)
        r.step('데모 로그인 → /app 진입', '/app' in page.url)

        # ③ 스케줄링 — 조율 기간·반복 요일 설정 → 정례 추천
        page.get_by_role('link', name='일정 조율', exact=True).click()
        page.wait_for_timeout(1500)
        page.get_by_label('조율 시작일').fill(RANGE_START)
        page.get_by_label('조율 종료일').fill(RANGE_END)
        for label in ('월', '수'):
            page.get_by_role('button', name=label, exact=True).click()
        page.get_by_role('button', name='AI 추천 슬롯 찾기').click()

        # 시리즈 카드 — 총 N회 배지·반복 주기·최저 회차·선호 적합도
        page.get_by_text(f'총 {COUNT}회').first.wait_for(timeout=20000)
        body = page.content()
        r.step('시리즈 카드 총 회차 배지', f'총 {COUNT}회' in body)
        r.step('반복 주기 표기 (매주 월·수)', '매주 월·수' in body)
        r.step('최저 회차 배지', '최저 회차' in body)
        r.step('선호 적합도 표기', '선호 적합도' in body)
        r.step('전 회차 공통 가능 표기', '전 회차 공통' in body)

        # 회차 펼침 — 첫 회차 날짜 행 확인
        page.get_by_role('button', name='회차별 보기').first.click()
        page.wait_for_timeout(500)
        r.step('회차별 펼침 (첫 회차 날짜)', '11/23 (월)' in page.content())

        # ④ 제안 모달 — 정기 일정 요약 → 제목 입력 → 전송
        page.get_by_role('button', name='제안 보내기').first.click()
        page.get_by_role('dialog').wait_for(timeout=5000)
        dialog_text = page.get_by_role('dialog').inner_text()
        r.step('제안 모달 정기 일정 요약', '정기 일정' in dialog_text and f'총 {COUNT}회' in dialog_text)
        r.step('제안 모달 첫 회차·기간 표기', '첫 회차' in dialog_text and '기간' in dialog_text)
        page.get_by_role('dialog').get_by_placeholder('예: 캡스톤 회의').fill('E2E 정례 회의')
        page.get_by_role('dialog').get_by_role('button', name='제안 보내기').click()
        page.get_by_text('제안을 보냈어요.').wait_for(timeout=10000)
        r.step('정례 제안 전송', True)

        # ⑤ 보관함 — 보낸 제안 시리즈 배지·외 N-1회·전체 취소
        page.get_by_role('link', name='제안 보관함', exact=True).first.click()
        page.wait_for_timeout(1200)
        page.get_by_role('tab', name='보낸 제안').click()
        page.wait_for_timeout(1200)
        sent_text = page.content()
        r.step('보관함 외 N-1회 표기', f'외 {COUNT - 1}회' in sent_text)
        r.step('보관함 반복 배지', f'매주 월·수 · 총 {COUNT}회' in sent_text)
        r.step('보관함 전체 취소 버튼', page.get_by_role('button', name='전체 취소').count() > 0)

        # ⑥ 채팅 — 시리즈 방 "총 N회" 배지
        page.get_by_role('link', name='채팅', exact=True).click()
        page.wait_for_timeout(1200)
        r.step('채팅방 총 N회 배지', page.get_by_text(f'총 {COUNT}회').count() > 0)

        browser.close()

    # ⑦ API 재확인 — Summary의 occurrenceCount + 상세의 occurrences 물질화
    proposals = api(token, 'GET', '/api/scheduling/proposals?box=sent&size=1').json()
    latest = proposals['content'][0]
    detail = api(token, 'GET', f"/api/scheduling/proposals/{latest['id']}").json()
    r.step(
        '제안 occurrenceCount=4·occurrences 4건',
        latest.get('occurrenceCount') == COUNT
        and len(detail.get('occurrences') or []) == COUNT
        and detail.get('recurrence', {}).get('byDay') == BYDAY,
        f"occurrenceCount={latest.get('occurrenceCount')}",
    )

    failed_api = [f for f in r.failed_responses if 'auth/refresh' not in f]
    print('\n실패 API 응답:', failed_api if failed_api else '없음')
    if r.console_errors:
        print('콘솔 에러:', json.dumps(r.console_errors[:5], ensure_ascii=False))
    passed = sum(1 for s in r.steps if s['ok'])
    print(f'\n결과: {passed}/{len(r.steps)} 단계 통과')
    out = ROOT / 'snapshots' / f"e2e-recurring-{__import__('datetime').datetime.now():%Y%m%d-%H%M%S}.json"
    out.write_text(
        json.dumps(
            {'steps': r.steps, 'consoleErrors': r.console_errors, 'failedResponses': r.failed_responses},
            ensure_ascii=False,
            indent=2,
        ),
        encoding='utf-8',
    )
    print(f'report: {out}')
    return 0 if passed == len(r.steps) else 1


if __name__ == '__main__':
    sys.exit(main())
