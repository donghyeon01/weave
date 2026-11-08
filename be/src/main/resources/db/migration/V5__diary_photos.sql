CREATE TABLE diary_photos (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    diary_id     BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(50)  NOT NULL,
    size_bytes   INT          NOT NULL,
    sort_order   INT          NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_diary_photos_diary FOREIGN KEY (diary_id) REFERENCES diaries (id) ON DELETE CASCADE,
    CONSTRAINT fk_diary_photos_user  FOREIGN KEY (user_id)  REFERENCES users (id)   ON DELETE CASCADE,
    CONSTRAINT uk_diary_photos_diary_sort UNIQUE (diary_id, sort_order),
    INDEX idx_diary_photos_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
