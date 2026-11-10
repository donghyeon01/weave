CREATE TABLE chat_rooms (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    proposal_id BIGINT       NOT NULL,
    title       VARCHAR(200) NOT NULL,
    mode        VARCHAR(4)   NOT NULL,
    start_time  DATETIME     NOT NULL,
    end_time    DATETIME     NOT NULL,
    status      VARCHAR(10)  NOT NULL DEFAULT 'OPEN',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    closed_at   DATETIME(6)  NULL,
    CONSTRAINT uk_chat_rooms_proposal UNIQUE (proposal_id),
    CONSTRAINT chk_chat_rooms_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_chat_rooms_mode   CHECK (mode IN ('TIME', 'DATE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_room_members (
    room_id              BIGINT      NOT NULL,
    user_id              BIGINT      NOT NULL,
    last_read_message_id BIGINT      NULL,
    joined_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (room_id, user_id),
    CONSTRAINT fk_chat_members_room FOREIGN KEY (room_id) REFERENCES chat_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_messages (
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id            BIGINT       NOT NULL,
    sender_id          BIGINT       NOT NULL,
    content            TEXT         NOT NULL,
    client_message_id  VARCHAR(64)  NULL,
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_chat_messages_room   FOREIGN KEY (room_id)   REFERENCES chat_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id)     ON DELETE CASCADE,
    CONSTRAINT uk_chat_messages_client UNIQUE (room_id, client_message_id),
    INDEX idx_chat_messages_room_id (room_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
