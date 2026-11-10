-- V2 share: 4단계 공개 범위 + 친구 그룹 + 피드 구독 (v1 share_targets 모델 폐기)
-- friend_groups를 shares보다 먼저 생성한다 — shares.group_id가 여기를 참조.

CREATE TABLE friend_groups (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id   BIGINT       NOT NULL,
    name       VARCHAR(30)  NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_friend_groups_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_friend_groups_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE friend_group_members (
    group_id   BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_fgm_group FOREIGN KEY (group_id) REFERENCES friend_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_fgm_user  FOREIGN KEY (user_id)  REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_fgm_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE feed_subscriptions (
    subscriber_id  BIGINT      NOT NULL,
    friend_user_id BIGINT      NOT NULL,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (subscriber_id, friend_user_id),
    CONSTRAINT fk_fs_subscriber FOREIGN KEY (subscriber_id)  REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_fs_friend     FOREIGN KEY (friend_user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_fs_friend (friend_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- group_id ON DELETE SET NULL: 그룹 삭제 시 GROUP 공개 항목은 비공개 취급된다
-- (자동 PRIVATE 전이가 아니라 열람 불가 — 소유자가 범위를 재설정한다).
CREATE TABLE shares (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id    BIGINT       NOT NULL,
    item_type   VARCHAR(5)   NOT NULL,
    item_id     BIGINT       NOT NULL,
    visibility  VARCHAR(7)   NOT NULL,
    group_id    BIGINT       NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NULL,
    CONSTRAINT uk_shares_item UNIQUE (owner_id, item_type, item_id),
    CONSTRAINT chk_shares_item_type CHECK (item_type IN ('DIARY', 'EVENT')),
    CONSTRAINT chk_shares_visibility CHECK (visibility IN ('PRIVATE', 'FRIENDS', 'GROUP', 'PUBLIC')),
    CONSTRAINT fk_shares_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_shares_group FOREIGN KEY (group_id) REFERENCES friend_groups (id) ON DELETE SET NULL
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
