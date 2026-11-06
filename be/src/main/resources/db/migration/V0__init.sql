-- 위브(Weave) 초기 스키마 — MySQL 8.x, InnoDB, utf8mb4

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    nickname      VARCHAR(20)  NOT NULL,
    profile_image VARCHAR(500) NULL,
    provider      VARCHAR(10)  NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_provider CHECK (provider IN ('kakao', 'demo'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tasks (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    title           VARCHAR(100) NOT NULL,
    completed       BOOLEAN      NOT NULL DEFAULT FALSE,
    due_date        DATE         NOT NULL,
    idempotency_key VARCHAR(64)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tasks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_tasks_idem UNIQUE (user_id, idempotency_key),
    INDEX idx_tasks_user_completed_due (user_id, completed, due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE events (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     VARCHAR(500) NULL,
    start_time      DATETIME     NOT NULL,
    end_time        DATETIME     NOT NULL,
    location        VARCHAR(255) NULL,
    all_day         BOOLEAN      NOT NULL DEFAULT FALSE,
    source          VARCHAR(10)  NOT NULL DEFAULT 'manual',
    idempotency_key VARCHAR(64)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_events_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_events_range CHECK (start_time < end_time),
    CONSTRAINT chk_events_source CHECK (source IN ('manual', 'proposal')),
    CONSTRAINT uk_events_idem UNIQUE (user_id, idempotency_key),
    INDEX idx_events_user_start (user_id, start_time),
    INDEX idx_events_user_end (user_id, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE diaries (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    date       DATE         NOT NULL,
    title      VARCHAR(100) NULL,
    content    TEXT         NOT NULL,
    mood       VARCHAR(20)  NULL,
    tags       JSON         NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_diaries_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_diaries_user_date UNIQUE (user_id, date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE friendships (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    friend_id  BIGINT      NOT NULL,
    status     VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_friendships_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_friendships_friend FOREIGN KEY (friend_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_friendships_pair UNIQUE (user_id, friend_id),
    CONSTRAINT chk_friendships_status CHECK (status IN ('PENDING', 'ACCEPTED')),
    CONSTRAINT chk_friendships_not_self CHECK (user_id <> friend_id),
    INDEX idx_friendships_friend_status (friend_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE refresh_tokens (
    id                  BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT      NOT NULL,
    token_hash          CHAR(64)    NOT NULL,
    previous_token_hash CHAR(64)    NULL,
    user_agent          VARCHAR(255) NOT NULL,
    ip_address          VARCHAR(45) NULL,
    expiry_date         DATETIME    NOT NULL,
    evicted_at          DATETIME    NULL,
    last_accessed_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_refresh_hash UNIQUE (token_hash),
    INDEX idx_refresh_user_accessed (user_id, last_accessed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notifications (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    type       VARCHAR(30) NOT NULL,
    payload    JSON        NOT NULL,
    read_at    DATETIME    NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_notifications_type CHECK (type IN (
        'FRIEND_REQUEST', 'FRIEND_ACCEPTED',
        'PROPOSAL_RECEIVED', 'PROPOSAL_CONFIRMED', 'PROPOSAL_DECLINED')),
    INDEX idx_notifications_user_created (user_id, created_at),
    INDEX idx_notifications_user_read (user_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE schedule_proposals (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organizer_id    BIGINT       NOT NULL,
    title           VARCHAR(200) NOT NULL,
    mode            VARCHAR(4)   NOT NULL,
    start_time      DATETIME     NOT NULL,
    end_time        DATETIME     NOT NULL,
    status          VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(64)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_proposals_organizer FOREIGN KEY (organizer_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_proposals_mode CHECK (mode IN ('TIME', 'DATE')),
    CONSTRAINT chk_proposals_status CHECK (status IN ('PENDING', 'CONFIRMED', 'DECLINED', 'CANCELLED')),
    CONSTRAINT chk_proposals_range CHECK (start_time < end_time),
    CONSTRAINT uk_proposals_idem UNIQUE (organizer_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE schedule_proposal_participants (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    proposal_id  BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    response     VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    responded_at DATETIME    NULL,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_participants_proposal FOREIGN KEY (proposal_id) REFERENCES schedule_proposals (id) ON DELETE CASCADE,
    CONSTRAINT fk_participants_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_participants_proposal_user UNIQUE (proposal_id, user_id),
    CONSTRAINT chk_participants_response CHECK (response IN ('PENDING', 'ACCEPTED', 'DECLINED')),
    INDEX idx_participants_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
