"""2계정 스케줄링 확정 E2E — 수락→confirm→EVENTS 물질화·PROPOSAL-CONFLICT.

V2 scheduling extension의 마지막 미검증 경로다. 데모 로그인은 demo@weave.local
단일 계정이라 제2 계정은 refresh_tokens 행 직접 발급으로 세션을 만든다
(scripts/e2e_share_flow.py와 같은 로컬 검증 한정 방식 — DEMO_ENABLED 환경 전용).

시나리오:
  1. A(demo)↔B(jiwoo) 친구 관계 보장
  2. A 정례 제안(월·수 3회) → B PROPOSAL_RECEIVED 알림(occurrenceCount)
  3. B ACCEPTED → ALL_ACCEPTED → A confirm → CONFIRMED
  4. A·B 각각 회차 수만큼 PROPOSAL 이벤트 물질화 검증
  5. A 두 번째 정례 제안(화·목 2회) → B ACCEPTED → B가 회차와 겹치는 HARD 이벤트 생성
  6. A confirm → 409 PROPOSAL-CONFLICT, 제안은 ALL_ACCEPTED 유지

사용:
    python scripts/e2e_scheduling_confirm.py

사전 조건: be(8081, DEMO_ENABLED=true)·weave-mysql 컨테이너 기동. fe 불필요(API 레벨).
결과 JSON은 snapshots/e2e-confirm-<timestamp>.json (git 제외).
"""

import hashlib
import json
import secrets
import subprocess
import sys
from datetime import datetime, timedelta
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parent.parent
API = 'http://localhost:8081'

DEMO_EMAIL = 'demo@weave.local'
JIWOO_EMAIL = 'demo.jiwoo@weave.local'

# 제안 1 — 월·수 3회 (2026-11-23 월 첫 회차 → 11/25 수, 11/30 월)
P1 = {
    'byDay': [1, 3],
    'count': 3,
    'slot': {'type': 'TIME', 'date': '2026-11-23', 'startTime': '19:00', 'endTime': '21:00'},
}
# 제안 2 — 화·목 2회 (2026-11-24 화 첫 회차 → 11/26 목). P1 회차와 날짜가 겹치지 않는다
P2 = {
    'byDay': [2, 4],
    'count': 2,
    'slot': {'type': 'TIME', 'date': '2026-11-24', 'startTime': '19:00', 'endTime': '21:00'},
}
# B의 차단 이벤트 — P2 첫 회차(11/24 19:00~21:00)와 겹치는 HARD 일정
BLOCK = {
    'title': 'E2E-CONFIRM-BLOCK',
    'startTime': '2026-11-24T19:30:00',
    'endTime': '2026-11-24T20:30:00',
    'busyPolicy': 'HARD',
}
EVENT_RANGE = {'start': '2026-11-20', 'end': '2026-12-05'}


class Reporter:
    def __init__(self):
        self.steps = []

    def step(self, name, ok, detail=''):
        self.steps.append({'step': name, 'ok': ok, 'detail': str(detail)[:300]})
        print(f"[{'PASS' if ok else 'FAIL'}] {name}{f' - {detail}' if detail else ''}")


r = Reporter()
# 재실행 시 이전 실행의 PROPOSAL 이벤트와 구분하기 위한 실행 고유 제목 접미사
RUN_ID = datetime.now().strftime('%H%M%S')


def mysql(sql):
    out = subprocess.run(
        ['docker', 'exec', 'weave-mysql', 'sh', '-c',
         f'mysql -uweave -p"$MYSQL_PASSWORD" weave -N -e "{sql}"'],
        capture_output=True, text=True)
    return out.stdout.strip()


def uid(email):
    return int(mysql(f"SELECT id FROM users WHERE email='{email}'"))


def make_session(user_id):
    """refresh_tokens 행을 만들고 /api/auth/refresh로 accessToken+회전 쿠키를 받는다."""
    raw = secrets.token_hex(32)
    h = hashlib.sha256(raw.encode()).hexdigest()
    expiry = (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S')
    mysql(f"INSERT INTO refresh_tokens (user_id, token_hash, user_agent, expiry_date, last_accessed_at) "
          f"VALUES ({user_id}, '{h}', 'e2e-confirm', '{expiry}', NOW())")
    res = requests.post(f'{API}/api/auth/refresh', cookies={'refresh_token': raw},
                        headers={'User-Agent': 'e2e-confirm'})
    res.raise_for_status()
    return res.json()['accessToken']


def call(token, method, path, **kw):
    return requests.request(method, f'{API}{path}',
                            headers={'Authorization': f'Bearer {token}'}, **kw)


def ensure_friendship(token_a, token_b, jiwoo_id):
    """A→B 친구 관계 보장 — 이전 실행의 해제 잔재를 API로 복구한다."""
    friends = call(token_a, 'GET', '/api/friends').json()
    friend_ids = {(f.get('user') or {}).get('id') for f in friends if isinstance(f, dict)}
    if jiwoo_id in friend_ids:
        return True
    res = call(token_a, 'POST', '/api/friends/requests', json={'email': JIWOO_EMAIL})
    rid = res.json().get('friendshipId') or res.json().get('id')
    if rid:
        res2 = call(token_b, 'POST', f'/api/friends/requests/{rid}/accept')
    else:
        # 409 잔재 — 받은 pending 요청을 찾아 수락 (어느 방향이든)
        for me, other in ((token_b, DEMO_EMAIL), (token_a, JIWOO_EMAIL)):
            pend = call(me, 'GET', '/api/friends/requests/received').json()
            hit = next((f for f in pend if isinstance(f, dict)
                        and (f.get('user') or {}).get('email') == other), None)
            if hit:
                rid = hit.get('friendshipId') or hit.get('id')
                call(me, 'POST', f'/api/friends/requests/{rid}/accept')
                break
        res2 = res
    friends = call(token_a, 'GET', '/api/friends').json()
    ok = any((f.get('user') or {}).get('id') == jiwoo_id for f in friends if isinstance(f, dict))
    r.step('A↔B 친구 관계 보장', ok, f'{res.status_code}→{res2.status_code}')
    return ok


def create_series_proposal(token_a, jiwoo_id, spec, tag):
    res = call(token_a, 'POST', '/api/scheduling/proposals', json={
        'title': f'E2E confirm {tag} {RUN_ID}',
        'slot': spec['slot'],
        'participantIds': [jiwoo_id],
        'recurrence': {'freq': 'WEEKLY', 'interval': 1,
                       'byDay': spec['byDay'], 'count': spec['count']},
    })
    if res.status_code not in (200, 201):
        return None, res
    d = res.json()
    return d, res


def proposal_events(token, title):
    res = call(token, 'GET', '/api/events/range', params=EVENT_RANGE)
    if res.status_code != 200:
        return []
    return [e for e in res.json()
            if str(e.get('source')).lower() == 'proposal' and e.get('title') == title]


def main():
    jiwoo_id = uid(JIWOO_EMAIL)

    login = requests.post(f'{API}/api/auth/demo',
                          headers={'User-Agent': 'e2e-confirm'}, timeout=15)
    login.raise_for_status()
    token_a = login.json()['accessToken']
    token_b = make_session(jiwoo_id)
    r.step('세션 2개 발급 (demo·jiwoo)', True)

    # 재실행 멱등 — 이전 실행의 차단 이벤트·확정 PROPOSAL 이벤트 잔재 제거.
    # 확정 이벤트는 HARD라 같은 회차 날짜의 재실행 confirm을 409로 만들므로 함께 지운다.
    mysql(f"DELETE FROM events WHERE user_id={jiwoo_id} AND title='{BLOCK['title']}'")
    mysql("DELETE FROM events WHERE title LIKE 'E2E confirm%'")

    if not ensure_friendship(token_a, token_b, jiwoo_id):
        return finish()

    # ── 제안 1: 정례 3회 → 전원 수락 → confirm → EVENTS 물질화 ──
    p1, res = create_series_proposal(token_a, jiwoo_id, P1, 'series-A')
    ok = (p1 is not None and p1.get('occurrenceCount') == P1['count']
          and len(p1.get('occurrences') or []) == P1['count']
          and p1.get('status') == 'PENDING')
    r.step('P1 정례 제안 생성 (occurrenceCount=3·occurrences 3건)', ok,
           f'{res.status_code} occ={p1 and p1.get("occurrenceCount")}')
    if p1 is None:
        return finish()
    p1_id, p1_title = p1['id'], p1['title']

    noti = call(token_b, 'GET', '/api/notifications?size=50').json()
    items = noti.get('content', noti) if isinstance(noti, dict) else noti
    hit = next((n for n in items if n.get('type') == 'PROPOSAL_RECEIVED'
                and n.get('payload', {}).get('proposalId') == p1_id), None)
    r.step('B에게 PROPOSAL_RECEIVED (occurrenceCount=3)',
           hit is not None and hit.get('payload', {}).get('occurrenceCount') == P1['count'])

    res = call(token_b, 'POST', f'/api/scheduling/proposals/{p1_id}/respond',
               json={'response': 'ACCEPTED'})
    ok = res.status_code == 200 and res.json().get('status') == 'ALL_ACCEPTED'
    r.step('B ACCEPTED → ALL_ACCEPTED', ok, f'{res.status_code} {res.text[:120]}')

    res = call(token_a, 'POST', f'/api/scheduling/proposals/{p1_id}/confirm')
    ok = res.status_code == 200 and res.json().get('status') == 'CONFIRMED'
    r.step('A confirm → CONFIRMED', ok, f'{res.status_code} {res.text[:120]}')

    for name, tok in (('A(demo)', token_a), ('B(jiwoo)', token_b)):
        evs = proposal_events(tok, p1_title)
        r.step(f'{name} PROPOSAL 이벤트 {P1["count"]}건 물질화',
               len(evs) == P1['count'], f'{len(evs)}건')

    # ── 제안 2: B가 회차와 겹치는 HARD 이벤트 선점 → confirm 409 ──
    p2, res = create_series_proposal(token_a, jiwoo_id, P2, 'series-B')
    ok = (p2 is not None and p2.get('occurrenceCount') == P2['count']
          and p2.get('status') == 'PENDING')
    r.step('P2 정례 제안 생성 (occurrenceCount=2)', ok, f'{res.status_code}')
    if p2 is None:
        return finish()
    p2_id = p2['id']

    res = call(token_b, 'POST', f'/api/scheduling/proposals/{p2_id}/respond',
               json={'response': 'ACCEPTED'})
    r.step('P2 B ACCEPTED → ALL_ACCEPTED',
           res.status_code == 200 and res.json().get('status') == 'ALL_ACCEPTED')

    res = call(token_b, 'POST', '/api/events', json=BLOCK)
    r.step('B HARD 차단 이벤트 생성 (11/24 19:30~20:30)',
           res.status_code in (200, 201), f'{res.status_code}')

    res = call(token_a, 'POST', f'/api/scheduling/proposals/{p2_id}/confirm')
    body = res.json() if res.headers.get('content-type', '').startswith('application/json') else {}
    r.step('P2 confirm → 409 PROPOSAL-CONFLICT',
           res.status_code == 409 and body.get('code') == 'PROPOSAL-CONFLICT',
           f'{res.status_code} {body.get("code")}')

    detail = call(token_a, 'GET', f'/api/scheduling/proposals/{p2_id}').json()
    r.step('충돌 후 제안 ALL_ACCEPTED 유지', detail.get('status') == 'ALL_ACCEPTED',
           detail.get('status'))
    # 충돌로 이벤트가 생기지 않았는지 — B의 차단 이벤트 외 PROPOSAL 이벤트 0건
    r.step('충돌 제안 EVENTS 미생성',
           len(proposal_events(token_b, p2['title'])) == 0)

    return finish()


def finish():
    fails = [s for s in r.steps if not s['ok']]
    print(f"\n결과: {len(r.steps) - len(fails)}/{len(r.steps)} 단계 통과")
    out = ROOT / 'snapshots' / f"e2e-confirm-{datetime.now():%Y%m%d-%H%M%S}.json"
    out.write_text(json.dumps({'steps': r.steps}, ensure_ascii=False, indent=2),
                   encoding='utf-8')
    print(f'report: {out.relative_to(ROOT)}')
    return 1 if fails else 0


if __name__ == '__main__':
    sys.exit(main())
