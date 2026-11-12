-- V11__scheduling_extension.sql
CREATE TABLE availability_preferred_windows (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    day_of_week TINYINT      NOT NULL,
    start_time  TIME         NOT NULL,
    end_time    TIME         NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_pref_windows_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_pref_windows_dow CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_pref_windows_range CHECK (start_time < end_time),
    INDEX idx_pref_windows_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE schedule_proposals
    ADD COLUMN recurrence_freq     VARCHAR(8)  NULL,
    ADD COLUMN recurrence_interval TINYINT     NULL,
    ADD COLUMN recurrence_by_days  VARCHAR(13) NULL,
    ADD COLUMN occurrence_count    TINYINT     NULL,
    ADD CONSTRAINT chk_proposals_recurrence CHECK (
        (recurrence_freq IS NULL AND recurrence_interval IS NULL
         AND recurrence_by_days IS NULL AND occurrence_count IS NULL)
        OR (recurrence_freq = 'WEEKLY' AND recurrence_interval BETWEEN 1 AND 4
            AND recurrence_by_days IS NOT NULL
            AND occurrence_count BETWEEN 2 AND 12));

CREATE TABLE schedule_proposal_occurrences (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    proposal_id BIGINT      NOT NULL,
    seq        INT          NOT NULL,
    start_time DATETIME(6)  NOT NULL,
    end_time   DATETIME(6)  NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_prop_occ_proposal FOREIGN KEY (proposal_id)
        REFERENCES schedule_proposals (id) ON DELETE CASCADE,
    CONSTRAINT uk_prop_occ_seq UNIQUE (proposal_id, seq),
    CONSTRAINT chk_prop_occ_range CHECK (start_time < end_time),
    INDEX idx_prop_occ_proposal (proposal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 기존 제안 백필: 각 제안 1행 (start_time/end_time 그대로)
INSERT INTO schedule_proposal_occurrences (proposal_id, seq, start_time, end_time)
SELECT id, 0, start_time, end_time FROM schedule_proposals;

ALTER TABLE chat_rooms
    ADD COLUMN occurrence_count INT NOT NULL DEFAULT 1;
