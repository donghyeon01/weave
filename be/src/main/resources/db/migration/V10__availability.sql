-- V2 wave-3 availability: 수동 가용 시간 3테이블 + events.busy_policy 조정 정책
-- 가용성 원본은 본인 전용 — users FK만 참조하고 타 모듈 테이블은 참조하지 않는다.

CREATE TABLE availability_scopes (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    mode        VARCHAR(4)   NOT NULL,
    range_start DATE         NOT NULL,
    range_end   DATE         NOT NULL,
    status      VARCHAR(9)   NOT NULL DEFAULT 'INPUT',
    revision    INT          NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_avail_scopes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_avail_scopes_mode CHECK (mode IN ('TIME','DATE')),
    CONSTRAINT chk_avail_scopes_status CHECK (status IN ('INPUT','COMPLETED','CANCELLED')),
    CONSTRAINT chk_avail_scopes_range CHECK (range_start <= range_end),
    INDEX idx_avail_scopes_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- updated_at 부재는 의도 — 블록은 PUT 전량 교체(삭제+삽입)로만 바뀌는 불변 행이다
CREATE TABLE availability_blocks (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    scope_id   BIGINT       NOT NULL,
    kind       VARCHAR(4)   NOT NULL,
    start_at   DATETIME(6)  NULL,
    end_at     DATETIME(6)  NULL,
    block_date DATE         NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_avail_blocks_scope FOREIGN KEY (scope_id) REFERENCES availability_scopes (id) ON DELETE CASCADE,
    CONSTRAINT chk_avail_blocks_kind CHECK (kind IN ('TIME','DATE')),
    CONSTRAINT chk_avail_blocks_time CHECK (
        (kind='TIME' AND start_at IS NOT NULL AND end_at IS NOT NULL AND block_date IS NULL)
        OR (kind='DATE' AND block_date IS NOT NULL AND start_at IS NULL AND end_at IS NULL)),
    INDEX idx_avail_blocks_scope (scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE availability_preferences (
    user_id                BIGINT      NOT NULL,
    use_calendar_inference BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_avail_prefs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- busy_policy는 시간 점유 의미(HARD 조율 불가/SOFT 조정 가능/FREE 비점유) — source와 독립.
-- DEFAULT 'HARD'로 기존 행 전부 조율 불가 점유로 채워지므로 별도 백필 불필요.
ALTER TABLE events
    ADD COLUMN busy_policy VARCHAR(4) NOT NULL DEFAULT 'HARD',
    ADD CONSTRAINT chk_events_busy_policy CHECK (busy_policy IN ('HARD','SOFT','FREE'));
