CREATE TABLE shares (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id    BIGINT       NOT NULL,
    item_type   VARCHAR(5)   NOT NULL,
    item_id     BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_shares_item UNIQUE (owner_id, item_type, item_id),
    CONSTRAINT chk_shares_item_type CHECK (item_type IN ('DIARY', 'EVENT')),
    CONSTRAINT fk_shares_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE share_targets (
    share_id   BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (share_id, user_id),
    CONSTRAINT fk_share_targets_share FOREIGN KEY (share_id) REFERENCES shares (id) ON DELETE CASCADE,
    CONSTRAINT fk_share_targets_user  FOREIGN KEY (user_id)  REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_share_targets_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE share_replies (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    share_id   BIGINT       NOT NULL,
    parent_id  BIGINT       NULL,
    author_id  BIGINT       NOT NULL,
    content    VARCHAR(500) NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NULL,
    deleted_at DATETIME(6)  NULL,
    CONSTRAINT fk_share_replies_share  FOREIGN KEY (share_id)  REFERENCES shares (id) ON DELETE CASCADE,
    CONSTRAINT fk_share_replies_parent FOREIGN KEY (parent_id) REFERENCES share_replies (id) ON DELETE CASCADE,
    CONSTRAINT fk_share_replies_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_share_replies_share_id (share_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE notifications
    DROP CHECK chk_notifications_type,
    ADD CONSTRAINT chk_notifications_type CHECK (type IN (
        'FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'PROPOSAL_RECEIVED', 'PROPOSAL_ALL_ACCEPTED',
        'PROPOSAL_CONFIRMED', 'PROPOSAL_DECLINED', 'SHARED_ITEM', 'SHARE_REPLY'));
