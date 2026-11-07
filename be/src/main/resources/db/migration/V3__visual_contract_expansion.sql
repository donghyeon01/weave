-- Phase V3 시각 계약 수렴 스키마 확장 — db-schema §5-1 명세 그대로.
-- enum은 §1 규약대로 MySQL ENUM 대신 VARCHAR + CHECK.

ALTER TABLE tasks
    ADD COLUMN  priority VARCHAR(10)  NOT NULL DEFAULT 'normal',
    ADD COLUMN  memo     VARCHAR(200) NULL,
    ADD CONSTRAINT chk_tasks_priority CHECK (priority IN ('urgent', 'high', 'normal', 'low'));

ALTER TABLE events
    ADD COLUMN proposal_id BIGINT NULL,
    ADD CONSTRAINT fk_events_proposal FOREIGN KEY (proposal_id) REFERENCES schedule_proposals (id) ON DELETE SET NULL;

ALTER TABLE diaries
    ADD COLUMN event_id BIGINT NULL,
    ADD CONSTRAINT chk_diaries_mood CHECK (mood IN ('happy', 'calm', 'busy', 'tired')),
    ADD CONSTRAINT fk_diaries_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE SET NULL;

ALTER TABLE friendships
    ADD COLUMN message VARCHAR(100) NULL;

ALTER TABLE users
    ADD COLUMN bio             VARCHAR(60) NULL,
    ADD COLUMN share_busy_only BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN share_titles    BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN share_details   BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN theme           VARCHAR(10) NOT NULL DEFAULT 'system',
    ADD CONSTRAINT chk_users_theme CHECK (theme IN ('light', 'dark', 'system'));
