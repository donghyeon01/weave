"""share 2계정 E2E — V2 wave-2 공개 범위·피드·구독·답글·친구 해제/재친구·그룹·비공개 시나리오.

데모 계정(A=demo@weave.local)은 /api/auth/demo로 로그인한다.
지우(B)·서준(C) 계정은 refresh_tokens 행을 직접 넣어 세션을 만든다(데모 로그인은 demo 고정이라
타 계정 로그인 경로가 없다 — 로컬 검증 한정).

사용:
    python scripts/e2e_share_flow.py [--base-url http://localhost:6200] [--api http://localhost:8081] [--ui]
사전 조건: be(8081, DEMO_ENABLED=true)·fe(6200) 기동, weave-mysql 컨테이너 실행.
결과 JSON은 snapshots/e2e-share-<timestamp>.json (git 제외).
"""

import argparse
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


class Reporter:
    def __init__(self):
        self.steps = []

    def step(self, name, ok, detail=''):
        self.steps.append({'step': name, 'ok': ok, 'detail': str(detail)[:300]})
        print(f"[{'PASS' if ok else 'FAIL'}] {name}{f' - {detail}' if detail else ''}")


r = Reporter()


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
          f"VALUES ({user_id}, '{h}', 'e2e-share', '{expiry}', NOW())")
    res = requests.post(f'{API}/api/auth/refresh', cookies={'refresh_token': raw},
                        headers={'User-Agent': 'e2e-share'})
    res.raise_for_status()
    return res.json()['accessToken'], res.cookies.get('refresh_token')


def call(token, method, path, **kw):
    res = requests.request(method, f'{API}{path}',
                           headers={'Authorization': f'Bearer {token}'}, **kw)
    return res


def expect_status(name, res, want):
    r.step(name, res.status_code == want, f'{res.status_code} {res.text[:160]}')


def main():
    global API
    ap = argparse.ArgumentParser()
    ap.add_argument('--base-url', default='http://localhost:6200')
    ap.add_argument('--api', default=API)
    ap.add_argument('--ui', action='store_true', help='playwright UI 패스 포함')
    args = ap.parse_args()
    API = args.api

    demo_id = uid('demo@weave.local')
    jiwoo_id = uid('demo.jiwoo@weave.local')
    seojun_id = uid('demo.seojun@weave.local')

    login = requests.post(f'{API}/api/auth/demo')
    token_a = login.json()['accessToken']
    token_b, cookie_b = make_session(jiwoo_id)
    token_c, _ = make_session(seojun_id)
    r.step('세션 3개 발급 (demo·jiwoo·seojun)', True)

    # 0. 선행 조건 — A↔B·C 친구 관계 보장 (이전 실행의 해제 잔재 복구)
    friends = call(token_a, 'GET', '/api/friends').json()
    friend_ids = {(f.get('user') or {}).get('id') for f in friends if isinstance(f, dict)}
    for name, email, tok in (('jiwoo', 'demo.jiwoo@weave.local', token_b),
                             ('seojun', 'demo.seojun@weave.local', token_c)):
        target = uid(email)
        if target in friend_ids:
            continue
        res = call(token_a, 'POST', '/api/friends/requests', json={'email': email})
        rid = res.json().get('friendshipId') or res.json().get('id')
        if not rid:
            # 409 잔재 — 받은 pending 요청을 찾아 수락 (어느 방향이든)
            for me, other_email in ((tok, 'demo@weave.local'), (token_a, email)):
                pend = call(me, 'GET', '/api/friends/requests/received').json()
                hit = next((f for f in pend if isinstance(f, dict)
                            and (f.get('user') or {}).get('email') == other_email), None)
                if hit:
                    rid = hit.get('friendshipId') or hit.get('id')
                    res2 = call(me, 'POST', f'/api/friends/requests/{rid}/accept')
                    break
            else:
                res2 = res
        else:
            res2 = call(tok, 'POST', f'/api/friends/requests/{rid}/accept')
        friends = call(token_a, 'GET', '/api/friends').json()
        ok = any((f.get('user') or {}).get('id') == target for f in friends if isinstance(f, dict))
        r.step(f'친구 관계 복구 ({name})', ok, f'{res.status_code}→{res2.status_code}')

    # 1. A 일기 작성 + FRIENDS 공개
    d1 = datetime.now().strftime('%Y-%m-%d')
    res = call(token_a, 'POST', '/api/diaries',
               json={'date': d1, 'title': 'E2E 공유 일기', 'content': '공개 범위 E2E 본문', 'tags': ['e2e']})
    r.step('A 일기 작성', res.status_code in (200, 201, 409), res.status_code)
    diary_id = int(mysql(f"SELECT id FROM diaries WHERE user_id={demo_id} AND date='{d1}' ORDER BY id DESC LIMIT 1"))
    res = call(token_a, 'PUT', '/api/shares',
               json={'itemType': 'DIARY', 'itemId': diary_id, 'visibility': 'FRIENDS'})
    share1 = res.json().get('shareId') or res.json().get('id')
    r.step('A 일기 FRIENDS 공개', res.status_code == 200, f'shareId={share1}')

    # 2. B 피드·프로필·상세 노출
    feed = call(token_b, 'GET', '/api/shares/feed?limit=20').json()
    feed = feed if isinstance(feed, list) else []
    r.step('B 피드에 A 항목 노출', any(s['shareId'] == share1 for s in feed))
    by = call(token_b, 'GET', f'/api/shares/by/{demo_id}').json()
    by = by if isinstance(by, list) else []
    r.step('B 프로필 목록 노출', any(s['shareId'] == share1 for s in by))
    res = call(token_b, 'GET', f'/api/shares/{share1}')
    r.step('B 상세 열람', res.status_code == 200 and res.json()['item']['title'] == 'E2E 공유 일기')

    # 3. B 구독 → A 신규 공개 → B SHARED_ITEM 알림
    res = call(token_b, 'PUT', f'/api/friends/{demo_id}/feed-subscription')
    r.step('B → A 피드 알람 구독', res.status_code == 204, res.status_code)
    d2 = (datetime.now() - timedelta(days=1)).strftime('%Y-%m-%d')
    res = call(token_a, 'POST', '/api/diaries', json={'date': d2, 'content': '구독 알림용 두 번째'})
    if res.status_code == 409:
        res = call(token_a, 'PATCH', f'/api/diaries/{d2}', json={'content': '구독 알림용 두 번째'})
    diary2 = int(mysql(f"SELECT id FROM diaries WHERE user_id={demo_id} AND date='{d2}' LIMIT 1"))
    res = call(token_a, 'PUT', '/api/shares',
               json={'itemType': 'DIARY', 'itemId': diary2, 'visibility': 'FRIENDS'})
    share2 = res.json().get('shareId') or res.json().get('id')
    noti = call(token_b, 'GET', '/api/notifications?size=20').json()
    items = noti.get('content', noti) if isinstance(noti, dict) else noti
    r.step('B에게 SHARED_ITEM 알림',
           any(n.get('type') == 'SHARED_ITEM' and n.get('payload', {}).get('shareId') == share2 for n in items),
           f'share2={share2}')

    # 4. B 답글 → A SHARE_REPLY 알림
    res = call(token_b, 'POST', f'/api/shares/{share1}/replies', json={'content': 'E2E 답글'})
    r.step('B 답글 작성', res.status_code in (200, 201), res.status_code)
    noti = call(token_a, 'GET', '/api/notifications?size=20').json()
    items = noti.get('content', noti) if isinstance(noti, dict) else noti
    r.step('A에게 SHARE_REPLY 알림',
           any(n.get('type') == 'SHARE_REPLY' and n.get('payload', {}).get('shareId') == share1 for n in items))

    # 5. A 친구 해제 → B 비가시 → 재친구 복원
    friends = call(token_a, 'GET', '/api/friends').json()
    fs = next((f for f in friends if (f.get('user') or {}).get('id') == jiwoo_id), None)
    fs_id = fs['friendshipId'] if fs else None
    res = call(token_a, 'DELETE', f'/api/friends/{fs_id}')
    r.step('A → B 친구 해제', res.status_code == 204, f'friendship={fs_id} {res.status_code}')
    feed = call(token_b, 'GET', '/api/shares/feed?limit=50').json()
    r.step('해제 후 B 피드 비가시', all(s['shareId'] != share1 for s in feed))
    res = call(token_b, 'GET', f'/api/shares/{share1}')
    r.step('해제 후 B 상세 404', res.status_code == 404, res.status_code)
    res = call(token_a, 'POST', '/api/friends/requests', json={'email': 'demo.jiwoo@weave.local'})
    req_id = res.json().get('friendshipId') or res.json().get('id')
    res2 = call(token_b, 'POST', f'/api/friends/requests/{req_id}/accept')
    r.step('재친구 (요청+수락)', res.status_code == 201 and res2.status_code == 200)
    res = call(token_b, 'GET', f'/api/shares/{share1}')
    r.step('재친구 후 복원 (상세 200)', res.status_code == 200, res.status_code)

    # 6. 그룹 공개 — 멤버(C)만 가시, B 비가시 (멱등: 기존 그룹 재사용)
    groups = call(token_a, 'GET', '/api/friend-groups').json()
    if groups:
        gid = groups[0].get('groupId') or groups[0].get('id')
        res = call(token_a, 'PUT', f'/api/friend-groups/{gid}/members',
                   json={'memberIds': [seojun_id]})
        r.step('A 그룹 재사용+멤버 교체 (서준만)', res.status_code in (200, 204), f'group={gid} {res.status_code}')
    else:
        res = call(token_a, 'POST', '/api/friend-groups',
                   json={'name': 'E2E 스터디', 'memberIds': [seojun_id]})
        gid = res.json().get('groupId') or res.json().get('id')
        r.step('A 그룹 생성 (서준만 멤버)', res.status_code in (200, 201), f'group={gid}')
    res = call(token_a, 'PUT', '/api/shares',
               json={'itemType': 'DIARY', 'itemId': diary2, 'visibility': 'GROUP', 'groupId': gid})
    r.step('share2 → GROUP 공개', res.status_code == 200, res.status_code)
    res_b = call(token_b, 'GET', f'/api/shares/{share2}')
    res_c = call(token_c, 'GET', f'/api/shares/{share2}')
    r.step('B(비멤버) 404 / C(멤버) 200', res_b.status_code == 404 and res_c.status_code == 200,
           f'B={res_b.status_code} C={res_c.status_code}')

    # 7. 비공개 전환 → 전원 비가시 + 답글 보존 → 재공개 복원
    res = call(token_a, 'PUT', '/api/shares',
               json={'itemType': 'DIARY', 'itemId': diary_id, 'visibility': 'PRIVATE'})
    r.step('share1 → PRIVATE', res.status_code == 200)
    res_b = call(token_b, 'GET', f'/api/shares/{share1}')
    res_c = call(token_c, 'GET', f'/api/shares/{share1}')
    r.step('B·C 모두 404', res_b.status_code == 404 and res_c.status_code == 404)
    res = call(token_a, 'PUT', '/api/shares',
               json={'itemType': 'DIARY', 'itemId': diary_id, 'visibility': 'FRIENDS'})
    reps = call(token_b, 'GET', f'/api/shares/{share1}/replies?limit=10').json()
    nodes = reps.get('content', reps) if isinstance(reps, dict) else reps
    r.step('재공개 → 답글 복원', res.status_code == 200 and len(nodes) >= 1, f'replies={len(nodes)}')

    # 8. mine 목록 — 비공개 제외 확인 위해 share1은 FRIENDS 상태
    mine = call(token_a, 'GET', '/api/shares/mine').json()
    r.step('A mine 목록 (공개 2건)', sum(1 for s in mine if s['shareId'] in (share1, share2)) == 2)

    if args.ui:
        ui_pass(jiwoo_id, args.base_url)

    fails = [s for s in r.steps if not s['ok']]
    print(f"\n결과: {len(r.steps) - len(fails)}/{len(r.steps)} 단계 통과")
    out = ROOT / 'snapshots' / f"e2e-share-{datetime.now():%Y%m%d-%H%M%S}.json"
    out.write_text(json.dumps({'steps': r.steps}, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'report: {out.relative_to(ROOT)}')
    return 1 if fails else 0


def ui_pass(jiwoo_id, base_url):
    """B(jiwoo) 브라우저 — 피드 탭 렌더 → 상세 진입까지 UI 경로 검증."""
    from playwright.sync_api import sync_playwright
    # 세션 UA 검증이 있으므로 컨텍스트 UA를 refresh_tokens 행과 일치시킨다
    ua = 'e2e-share-browser'
    raw = secrets.token_hex(32)
    h = hashlib.sha256(raw.encode()).hexdigest()
    mysql(f"INSERT INTO refresh_tokens (user_id, token_hash, user_agent, expiry_date, last_accessed_at) "
          f"VALUES ({jiwoo_id}, '{h}', '{ua}', NOW() + INTERVAL 30 DAY, NOW())")
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, user_agent=ua)
        ctx.add_cookies([{'name': 'refresh_token', 'value': raw,
                          'domain': 'localhost', 'path': '/'}])
        page = ctx.new_page()
        try:
            page.goto(base_url + '/app/friends?tab=feed', wait_until='load')
            page.wait_for_timeout(6000)
            logged = '/app' in page.url
            r.step('B 세션 복원(쿠키 로그인)', logged, page.url)
            if logged:
                card = page.get_by_text('E2E 공유 일기')
                r.step('B 피드 탭 카드 렌더', card.count() > 0)
                if card.count() > 0:
                    card.first.click()
                    page.wait_for_timeout(2000)
                    r.step('B 상세 진입 + 답글 노출',
                           '/app/shared/' in page.url and page.get_by_text('E2E 답글').count() > 0,
                           page.url)
        except Exception as e:
            r.step('B UI 패스 예외', False, f'{type(e).__name__}: {e}')
        finally:
            browser.close()


if __name__ == '__main__':
    sys.exit(main())
