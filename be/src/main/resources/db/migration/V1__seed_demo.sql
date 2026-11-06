-- 데모 사용자 단일 원천 — V2 시드(친구·일정)는 Task 16
INSERT INTO users(email, name, nickname, provider)
SELECT 'demo@weave.local', '데모', '데모유저', 'demo'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'demo@weave.local');
