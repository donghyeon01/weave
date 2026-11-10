-- ALL_ACCEPTED(12자)는 기존 status VARCHAR(10)에 안 들어간다 — 컬럼 확장이 CHECK 갱신과 동일 마이그레이션에 필수.
ALTER TABLE schedule_proposals
    MODIFY status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    DROP CHECK chk_proposals_status,
    ADD CONSTRAINT chk_proposals_status
        CHECK (status IN ('PENDING', 'ALL_ACCEPTED', 'CONFIRMED', 'DECLINED', 'CANCELLED'));

ALTER TABLE notifications
    DROP CHECK chk_notifications_type,
    ADD CONSTRAINT chk_notifications_type CHECK (type IN (
        'FRIEND_REQUEST', 'FRIEND_ACCEPTED',
        'PROPOSAL_RECEIVED', 'PROPOSAL_ALL_ACCEPTED',
        'PROPOSAL_CONFIRMED', 'PROPOSAL_DECLINED'));
