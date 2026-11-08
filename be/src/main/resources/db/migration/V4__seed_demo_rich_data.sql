-- 데모 계정 탐색용 보강 시드 — 2026-09-08 ~ 2027-01-08 범위(오늘 ±2개월)에 주 2~3건 밀도.
-- V2와 동일하게 전부 WHERE NOT EXISTS 멱등 — 재적용해도 행이 늘지 않는다.
-- events: 알고리즘 스터디 격주 패턴 + 일회성 약속. tasks: 과거 완료 + 미래 미완 혼합.
-- diaries는 과거~오늘만 (미래 일기는 부자연) — (user_id, date) 유니크로 멱등.

-- 1) events — 9월
INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '알고리즘 스터디', '격주 목요일 정례 — 그래프 파트', '2026-09-10 19:00:00', '2026-09-10 21:00:00',
       '스터디카페 연남점', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '알고리즘 스터디' AND start_time = '2026-09-10 19:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '지우랑 전시회', '디뮤지엄 《빛의 정원》', '2026-09-13 14:00:00', '2026-09-13 17:00:00',
       '디뮤지엄', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '지우랑 전시회' AND start_time = '2026-09-13 14:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '지도교수 면담', '캡스톤 진행 상황 공유', '2026-09-17 10:00:00', '2026-09-17 11:00:00',
       '공학관 415호', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '지도교수 면담' AND start_time = '2026-09-17 10:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '알고리즘 스터디', '격주 목요일 정례 — DP 파트', '2026-09-24 19:00:00', '2026-09-24 21:00:00',
       '스터디카페 연남점', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '알고리즘 스터디' AND start_time = '2026-09-24 19:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '추석 — 본가', NULL, '2026-09-25 00:00:00', '2026-09-27 23:59:00',
       '대전', TRUE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '추석 — 본가' AND start_time = '2026-09-25 00:00:00');

-- 2) events — 10월
INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '동아리 MT', '1박 2일 가을 MT', '2026-10-03 15:00:00', '2026-10-04 12:00:00',
       '가평 펜션', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '동아리 MT' AND start_time = '2026-10-03 15:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '알고리즘 스터디', '격주 목요일 정례 — 문자열 파트', '2026-10-08 19:00:00', '2026-10-08 21:00:00',
       '스터디카페 연남점', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '알고리즘 스터디' AND start_time = '2026-10-08 19:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '수아 생일 점심', NULL, '2026-10-10 12:00:00', '2026-10-10 14:00:00',
       '망원동 파스타집', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '수아 생일 점심' AND start_time = '2026-10-10 12:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '중간고사 기간', NULL, '2026-10-19 00:00:00', '2026-10-23 23:59:00',
       NULL, TRUE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '중간고사 기간' AND start_time = '2026-10-19 00:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '알고리즘 스터디', '격주 목요일 정례 — 시험 끝 기념 짧게', '2026-10-22 19:00:00', '2026-10-22 20:30:00',
       '스터디카페 연남점', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '알고리즘 스터디' AND start_time = '2026-10-22 19:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '할로윈 모임', '지우·서준·수아 코스튬 파티', '2026-10-31 18:00:00', '2026-10-31 21:00:00',
       '홍대', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '할로윈 모임' AND start_time = '2026-10-31 18:00:00');

-- 3) events — 11월 (오늘 11-08 기준 전후)
INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '알고리즘 스터디', '격주 목요일 정례 — 그리디 파트', '2026-11-05 19:00:00', '2026-11-05 21:00:00',
       '스터디카페 연남점', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '알고리즘 스터디' AND start_time = '2026-11-05 19:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '주말 러닝', '한강 5km 가볍게', '2026-11-07 11:00:00', '2026-11-07 12:00:00',
       '여의도 한강공원', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '주말 러닝' AND start_time = '2026-11-07 11:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '치과 예약', '정기 스케일링', '2026-11-11 15:00:00', '2026-11-11 16:00:00',
       '연세스마일치과', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '치과 예약' AND start_time = '2026-11-11 15:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '캡스톤 팀 회의', '최종 발표 역할 분담', '2026-11-17 14:00:00', '2026-11-17 16:00:00',
       '공학관 스터디룸 B', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '캡스톤 팀 회의' AND start_time = '2026-11-17 14:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '서준이랑 볼링', NULL, '2026-11-21 13:00:00', '2026-11-21 15:00:00',
       '신촌 볼링장', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '서준이랑 볼링' AND start_time = '2026-11-21 13:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '알고리즘 스터디', '격주 목요일 정례 — 트리 파트', '2026-11-26 19:00:00', '2026-11-26 21:00:00',
       '스터디카페 연남점', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '알고리즘 스터디' AND start_time = '2026-11-26 19:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '외할머니 생신', NULL, '2026-11-29 00:00:00', '2026-11-29 23:59:00',
       '대전', TRUE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '외할머니 생신' AND start_time = '2026-11-29 00:00:00');

-- 4) events — 12월
INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '알고리즘 스터디', '격주 목요일 정례 — 모의 코테', '2026-12-03 19:00:00', '2026-12-03 21:00:00',
       '스터디카페 연남점', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '알고리즘 스터디' AND start_time = '2026-12-03 19:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '캡스톤 최종 발표 리허설', NULL, '2026-12-08 14:00:00', '2026-12-08 16:00:00',
       '공학관 스터디룸 A', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '캡스톤 최종 발표 리허설' AND start_time = '2026-12-08 14:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '캡스톤 최종 발표', '발표 20분 + 시연 10분', '2026-12-11 13:00:00', '2026-12-11 15:00:00',
       '공학관 대강당', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '캡스톤 최종 발표' AND start_time = '2026-12-11 13:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '동아리 송년회', NULL, '2026-12-19 18:00:00', '2026-12-19 21:00:00',
       '합정동', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '동아리 송년회' AND start_time = '2026-12-19 18:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '크리스마스 이브 모임', '지우·수아랑 저녁', '2026-12-24 17:00:00', '2026-12-24 19:00:00',
       '을지로', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '크리스마스 이브 모임' AND start_time = '2026-12-24 17:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '부산 여행', '종강 기념 2박 3일', '2026-12-30 00:00:00', '2027-01-01 23:59:00',
       '부산', TRUE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '부산 여행' AND start_time = '2026-12-30 00:00:00');

-- 5) events — 1월
INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '겨울 인턴 면접', NULL, '2027-01-05 10:00:00', '2027-01-05 11:00:00',
       '온라인 (Zoom)', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '겨울 인턴 면접' AND start_time = '2027-01-05 10:00:00');

INSERT INTO events (user_id, title, description, start_time, end_time, location, all_day, source)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '새해 스터디 킥오프', '겨울방학 커리큘럼 조율', '2027-01-07 19:00:00', '2027-01-07 21:00:00',
       '스터디카페 연남점', FALSE, 'manual'
WHERE NOT EXISTS (
    SELECT 1 FROM events
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '새해 스터디 킥오프' AND start_time = '2027-01-07 19:00:00');

-- 6) tasks — 과거 완료 + 미래 미완 혼합
INSERT INTO tasks (user_id, title, completed, due_date, priority, memo)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '알고리즘 과제 1 제출', TRUE, '2026-09-12', 'high', '그래프 BFS/DFS 응용'
WHERE NOT EXISTS (
    SELECT 1 FROM tasks
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '알고리즘 과제 1 제출' AND due_date = '2026-09-12');

INSERT INTO tasks (user_id, title, completed, due_date, priority, memo)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '전시회 티켓 예매', TRUE, '2026-09-11', 'normal', '디뮤지엄 주말 오후'
WHERE NOT EXISTS (
    SELECT 1 FROM tasks
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '전시회 티켓 예매' AND due_date = '2026-09-11');

INSERT INTO tasks (user_id, title, completed, due_date, priority, memo)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       'OS 과제 2', TRUE, '2026-10-07', 'normal', '스케줄링 시뮬레이션'
WHERE NOT EXISTS (
    SELECT 1 FROM tasks
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = 'OS 과제 2' AND due_date = '2026-10-07');

INSERT INTO tasks (user_id, title, completed, due_date, priority, memo)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '수아 생일선물 준비', TRUE, '2026-10-09', 'high', '캔들+손편지'
WHERE NOT EXISTS (
    SELECT 1 FROM tasks
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '수아 생일선물 준비' AND due_date = '2026-10-09');

INSERT INTO tasks (user_id, title, completed, due_date, priority, memo)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '중간고사 범위 정리', TRUE, '2026-10-16', 'urgent', 'OS 1~6주차 / DB 1~7주차'
WHERE NOT EXISTS (
    SELECT 1 FROM tasks
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '중간고사 범위 정리' AND due_date = '2026-10-16');

INSERT INTO tasks (user_id, title, completed, due_date, priority, memo)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '도서관 책 반납', TRUE, '2026-10-28', 'low', '운영체제 원리 2권'
WHERE NOT EXISTS (
    SELECT 1 FROM tasks
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '도서관 책 반납' AND due_date = '2026-10-28');

INSERT INTO tasks (user_id, title, completed, due_date, priority, memo)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '캡스톤 중간 발표 슬라이드', TRUE, '2026-11-10', 'urgent', '데모 영상 30초 포함'
WHERE NOT EXISTS (
    SELECT 1 FROM tasks
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '캡스톤 중간 발표 슬라이드' AND due_date = '2026-11-10');

INSERT INTO tasks (user_id, title, completed, due_date, priority, memo)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '운동화 새로 사기', FALSE, '2026-11-15', 'low', '러닝화 쿠션감 좋은 걸로'
WHERE NOT EXISTS (
    SELECT 1 FROM tasks
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '운동화 새로 사기' AND due_date = '2026-11-15');

INSERT INTO tasks (user_id, title, completed, due_date, priority, memo)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '알고리즘 과제 3', FALSE, '2026-11-18', 'high', '트리 순회 + 최소 스패닝'
WHERE NOT EXISTS (
    SELECT 1 FROM tasks
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '알고리즘 과제 3' AND due_date = '2026-11-18');

INSERT INTO tasks (user_id, title, completed, due_date, priority, memo)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '통신비 자동이체 확인', FALSE, '2026-11-25', 'normal', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM tasks
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '통신비 자동이체 확인' AND due_date = '2026-11-25');

INSERT INTO tasks (user_id, title, completed, due_date, priority, memo)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '기말고사 시간표 정리', FALSE, '2026-12-01', 'normal', '과목별 날짜·장소 메모'
WHERE NOT EXISTS (
    SELECT 1 FROM tasks
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '기말고사 시간표 정리' AND due_date = '2026-12-01');

INSERT INTO tasks (user_id, title, completed, due_date, priority, memo)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '캡스톤 최종 보고서 초안', FALSE, '2026-12-04', 'high', '15p 분량, 팀 공동 작성'
WHERE NOT EXISTS (
    SELECT 1 FROM tasks
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '캡스톤 최종 보고서 초안' AND due_date = '2026-12-04');

INSERT INTO tasks (user_id, title, completed, due_date, priority, memo)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '송년회 장소 예약', FALSE, '2026-12-12', 'normal', '8명, 합정 근처'
WHERE NOT EXISTS (
    SELECT 1 FROM tasks
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '송년회 장소 예약' AND due_date = '2026-12-12');

INSERT INTO tasks (user_id, title, completed, due_date, priority, memo)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '기말 레포트 제출', FALSE, '2026-12-14', 'urgent', 'DB 설계 레포트 최종본'
WHERE NOT EXISTS (
    SELECT 1 FROM tasks
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '기말 레포트 제출' AND due_date = '2026-12-14');

INSERT INTO tasks (user_id, title, completed, due_date, priority, memo)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '부산 여행 숙소 예약', FALSE, '2026-12-20', 'high', '광안리 근처, 2박'
WHERE NOT EXISTS (
    SELECT 1 FROM tasks
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '부산 여행 숙소 예약' AND due_date = '2026-12-20');

INSERT INTO tasks (user_id, title, completed, due_date, priority, memo)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '이력서·포트폴리오 업데이트', FALSE, '2027-01-02', 'high', '인턴 지원용 — 캡스톤 결과 반영'
WHERE NOT EXISTS (
    SELECT 1 FROM tasks
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '이력서·포트폴리오 업데이트' AND due_date = '2027-01-02');

INSERT INTO tasks (user_id, title, completed, due_date, priority, memo)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '인턴 면접 준비', FALSE, '2027-01-04', 'urgent', 'CS 기초 + 프로젝트 회고 정리'
WHERE NOT EXISTS (
    SELECT 1 FROM tasks
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND title = '인턴 면접 준비' AND due_date = '2027-01-04');

-- 7) diaries — 과거~오늘만, 주 1~2회 밀도
INSERT INTO diaries (user_id, date, title, content, mood, tags)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '2026-09-10', '스터디 다시 시작',
       '방학 끝나고 오랜만에 스터디 모였다. 그래프 파트 들어가는데 다들 방학 동안 안 놓친 게 신기하다. 나도 꾸준히 해야지.',
       'calm', JSON_ARRAY('스터디', '알고리즘')
WHERE NOT EXISTS (
    SELECT 1 FROM diaries
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND date = '2026-09-10');

INSERT INTO diaries (user_id, date, title, content, mood, tags)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '2026-09-13', '지우랑 전시회',
       '디뮤지엄 빛의 정원 다녀왔다. 미디어아트는 볼 때마다 사진이 잘 안 담겨서 아쉽지만 눈으로는 최고였다. 지우가 예매해줘서 편하게 감.',
       'happy', JSON_ARRAY('친구', '전시')
WHERE NOT EXISTS (
    SELECT 1 FROM diaries
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND date = '2026-09-13');

INSERT INTO diaries (user_id, date, title, content, mood, tags)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '2026-09-20', '비 오는 일요일',
       '하루 종일 비. 과제하다가 창밖만 봤다. 이런 날은 그냥 아무것도 안 하고 싶은데 현실은 과제가 기다린다.',
       'tired', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM diaries
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND date = '2026-09-20');

INSERT INTO diaries (user_id, date, title, content, mood, tags)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '2026-09-25', '추석',
       '대전 본가 내려왔다. 할머니가 용돈 주시면서 공부하라고 하셨다. 맛있는 거 많이 먹고 올라오면 다시 일상.',
       'calm', JSON_ARRAY('가족')
WHERE NOT EXISTS (
    SELECT 1 FROM diaries
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND date = '2026-09-25');

INSERT INTO diaries (user_id, date, title, content, mood, tags)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '2026-10-04', 'MT 후기',
       '동아리 MT 1박 2일 끝. 새내기들이랑 친해져서 좋았다. 밤에 한 얘기들이 생각보다 진솔해서 오래 기억에 남을 것 같다.',
       'happy', JSON_ARRAY('동아리')
WHERE NOT EXISTS (
    SELECT 1 FROM diaries
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND date = '2026-10-04');

INSERT INTO diaries (user_id, date, title, content, mood, tags)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '2026-10-10', '수아 생일',
       '수아 생일 점심. 캔들이랑 손편지 줬는데 좋아해서 다행이다. 망원동 파스타집 생각보다 맛있었음 — 다음에 또 오자고 함.',
       'happy', JSON_ARRAY('친구', '생일')
WHERE NOT EXISTS (
    SELECT 1 FROM diaries
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND date = '2026-10-10');

INSERT INTO diaries (user_id, date, title, content, mood, tags, event_id)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '2026-10-21', '중간고사 진행 중',
       'OS 시험은 생각보다 무난했는데 DB가 어려웠다. 정규화 문제에서 헤맸다. 남은 이틀 더 버티자.',
       'busy', JSON_ARRAY('시험'),
       (SELECT id FROM events WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
        AND title = '중간고사 기간' LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1 FROM diaries
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND date = '2026-10-21');

INSERT INTO diaries (user_id, date, title, content, mood, tags)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '2026-10-23', '중간고사 끝!',
       '드디어 시험 주간 종료. 스터디에서 다들 짧게 만나서 수다만 떨다 왔다. 일단 자유를 만끽하는 주말.',
       'happy', JSON_ARRAY('시험', '일상')
WHERE NOT EXISTS (
    SELECT 1 FROM diaries
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND date = '2026-10-23');

INSERT INTO diaries (user_id, date, title, content, mood, tags, event_id)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '2026-10-31', '할로윈',
       '코스튬 파티 다녀왔다. 서준이 코스프레 퀄리티가 어마어마. 사진 많이 찍었다. 홍대는 역시 사람이 많다.',
       'happy', JSON_ARRAY('친구', '모임'),
       (SELECT id FROM events WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
        AND title = '할로윈 모임' LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1 FROM diaries
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND date = '2026-10-31');

INSERT INTO diaries (user_id, date, title, content, mood, tags)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '2026-11-01', '11월 시작',
       '벌써 11월. 중간 발표, 기말고사, 종강까지 숨 가쁘게 지나갈 달이다. 이번 달 목표는 수면 시간 지키기.',
       'calm', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM diaries
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND date = '2026-11-01');

INSERT INTO diaries (user_id, date, title, content, mood, tags, event_id)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '2026-11-07', '오랜만에 러닝',
       '한강에서 5km 뛰었다. 시험 기간에 멈췄더니 확실히 체력이 떨어진 느낌. 그래도 상쾌하다. 운동화가 닳아서 새로 사야 할 듯.',
       'calm', JSON_ARRAY('운동'),
       (SELECT id FROM events WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
        AND title = '주말 러닝' LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1 FROM diaries
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND date = '2026-11-07');

INSERT INTO diaries (user_id, date, title, content, mood, tags)
SELECT (SELECT id FROM users WHERE email = 'demo@weave.local'),
       '2026-11-08', '발표 준비 주간',
       '캡스톤 중간 발표가 이번 주 목요일이다. 슬라이드는 거의 다 했고 데모 영상만 정리하면 된다. 잘되길.',
       'busy', JSON_ARRAY('캡스톤')
WHERE NOT EXISTS (
    SELECT 1 FROM diaries
    WHERE user_id = (SELECT id FROM users WHERE email = 'demo@weave.local')
      AND date = '2026-11-08');
