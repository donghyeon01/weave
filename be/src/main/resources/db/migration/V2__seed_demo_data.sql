-- 데모 탐색용 시드 — 데모 사용자(demo@weave.local, V1 원천)에게 수락 친구 3명과 샘플 일정.
-- 전부 WHERE NOT EXISTS로 멱등 — 재적용해도 행이 늘지 않는다.

-- 1) 데모 친구 계정 3명
INSERT INTO users (email, name, nickname, provider)
SELECT 'demo.jiwoo@weave.local', '김지우', '지우', 'demo'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'demo.jiwoo@weave.local');

INSERT INTO users (email, name, nickname, provider)
SELECT 'demo.seojun@weave.local', '박서준', '서준', 'demo'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'demo.seojun@weave.local');

INSERT INTO users (email, name, nickname, provider)
SELECT 'demo.sua@weave.local', '이수아', '수아', 'demo'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'demo.sua@weave.local');

-- 2) 수락 친구 관계 (데모 → 친구 3명, ACCEPTED)
INSERT INTO friendships (user_id, friend_id, status)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       (SELECT id FROM users WHERE email = 'demo.jiwoo@weave.local'), 'ACCEPTED'
WHERE NOT EXISTS (
    SELECT 1 FROM friendships
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND friend_id = (SELECT id FROM users WHERE email = 'demo.jiwoo@weave.local'));

INSERT INTO friendships (user_id, friend_id, status)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       (SELECT id FROM users WHERE email = 'demo.seojun@weave.local'), 'ACCEPTED'
WHERE NOT EXISTS (
    SELECT 1 FROM friendships
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND friend_id = (SELECT id FROM users WHERE email = 'demo.seojun@weave.local'));

INSERT INTO friendships (user_id, friend_id, status)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       (SELECT id FROM users WHERE email = 'demo.sua@weave.local'), 'ACCEPTED'
WHERE NOT EXISTS (
    SELECT 1 FROM friendships
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND friend_id = (SELECT id FROM users WHERE email = 'demo.sua@weave.local'));

-- 3) 데모 사용자 샘플 일정 (수동 등록 3건 + 조율 확정 1건 — 범례 3카테고리 확인용)
INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '알고리즘 스터디', '매주 화요일 정례 스터디', '2026-11-10 19:00:00', '2026-11-10 21:00:00',
       '스터디카페 연남점', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '알고리즘 스터디' AND start_time = '2026-11-10 19:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '캡스톤 중간 발표', '발표 15분 + 질의 10분', '2026-11-12 14:00:00', '2026-11-12 15:00:00',
       '공학관 302호', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '캡스톤 중간 발표' AND start_time = '2026-11-12 14:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '기말고사', NULL, '2026-12-15 00:00:00', '2026-12-15 23:59:00', NULL, TRUE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '기말고사' AND start_time = '2026-12-15 00:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '스터디 회식', '조율로 확정된 약속 — 범례 확인용', '2026-11-14 18:00:00', '2026-11-14 20:00:00',
       '연남동', FALSE, 'proposal'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '스터디 회식' AND start_time = '2026-11-14 18:00:00');

-- 4) 친구들의 일정 — 조율 추천을 실행했을 때 busy가 다르게 나오도록 몇 건 배치
INSERT INTO events (user_id, title, start_time, end_time, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo.jiwoo@weave.local'),
       '지우 개인 약속', '2026-11-11 10:00:00', '2026-11-11 12:00:00', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo.jiwoo@weave.local')
      AND title = '지우 개인 약속');

INSERT INTO events (user_id, title, start_time, end_time, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo.seojun@weave.local'),
       '서준 알바', '2026-11-11 14:00:00', '2026-11-11 18:00:00', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo.seojun@weave.local')
      AND title = '서준 알바');
