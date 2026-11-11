# T11 E2E — availability → scheduling synthesis (API 레벨, dev JWT 발급)
# 사용: python scripts/e2e_availability.py
import base64
import hashlib
import hmac
import json
import time
import urllib.error
import urllib.request

BASE = 'http://localhost:8081'
SECRET = b'dev-only-secret-override-with-env-32bytes!!'


def b64u(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b'=').decode()


def mint(uid: int) -> str:
    h = b64u(json.dumps({'alg': 'HS256', 'typ': 'JWT'}, separators=(',', ':')).encode())
    now = int(time.time())
    p = b64u(json.dumps({'sub': str(uid), 'roles': ['ROLE_USER'], 'iat': now, 'exp': now + 1800},
                        separators=(',', ':')).encode())
    sig = b64u(hmac.new(SECRET, f'{h}.{p}'.encode(), hashlib.sha256).digest())
    return f'{h}.{p}.{sig}'


def call(method: str, path: str, token: str, body=None, headers=None):
    req = urllib.request.Request(
        BASE + path, method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={'Content-Type': 'application/json', 'Authorization': f'Bearer {token}',
                 **(headers or {})})
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return r.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:400]


def show(label, status, data):
    print(f'\n=== {label} -> {status}')
    print(json.dumps(data, ensure_ascii=False, indent=1)[:2000] if not isinstance(data, str) else data)


def main():
    t1, t2 = mint(1), mint(2)

    # 1) user1 availability — 11-12 09:00~12:00 TIME 블록으로 확장 후 완료
    s, d = call('GET', '/api/availability/scopes/1', t1)
    rev = d['revision']
    s, d = call('PUT', '/api/availability/scopes/1/blocks', t1, {
        'blocks': [{'kind': 'TIME', 'startAt': '2026-11-12T09:00:00', 'endAt': '2026-11-12T12:00:00'}],
        'expectedRevision': rev, 'status': 'COMPLETED'})
    show('user1 availability 완료(09-12시)', s, d)

    # 2) user1이 user2 포함 추천 — 08~13시, 30분 스텝, 60분 슬롯
    REC = {'mode': 'TIME', 'rangeStart': '2026-11-12', 'rangeEnd': '2026-11-12',
           'participantIds': [2], 'windowStart': '08:00', 'windowEnd': '13:00',
           'stepMinutes': 30, 'slotMinutes': 60}
    s, d = call('POST', '/api/scheduling', t1, REC)
    show('추천 baseline (이벤트 없음)', s, d)

    # 3) user1 HARD 이벤트 10~11시 → 재추천
    s, d = call('POST', '/api/events', t1, {
        'title': 'e2e-hard', 'startTime': '2026-11-12T10:00:00', 'endTime': '2026-11-12T11:00:00',
        'busyPolicy': 'HARD'}, {'Idempotency-Key': f'e2e-hard-{int(time.time())}'})
    show('HARD 이벤트 생성', s, d)
    eid = d.get('id') if isinstance(d, dict) else None
    s, d = call('POST', '/api/scheduling', t1, REC)
    show('추천 HARD 이후', s, d)

    # 4) SOFT로 변경 → 재추천
    if eid:
        s, d = call('PATCH', f'/api/events/{eid}', t1, {'busyPolicy': 'SOFT'})
        show('이벤트 SOFT 변경', s, d)
    s, d = call('POST', '/api/scheduling', t1, REC)
    show('추천 SOFT 이후', s, d)

    # 5) FREE로 변경 → 재추천 (점유 제외 기대)
    if eid:
        s, d = call('PATCH', f'/api/events/{eid}', t1, {'busyPolicy': 'FREE'})
        show('이벤트 FREE 변경', s, d)
    s, d = call('POST', '/api/scheduling', t1, REC)
    show('추천 FREE 이후', s, d)

    # 6) 정리 — 이벤트 삭제
    if eid:
        s, d = call('DELETE', f'/api/events/{eid}', t1)
        show('이벤트 삭제', s, d)


if __name__ == '__main__':
    main()
