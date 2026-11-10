package app.weave.share.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 항목별 공유 1행 — uk_shares_item(owner_id, item_type, item_id)이 항목당 공유 1개를 강제한다.
 * 불변 계열이라 BaseTimeEntity를 쓰지 않는다 (DiaryPhoto 선례).
 * item_id는 diary·event 테이블로의 논리 참조 — 존재·소유 검증은 읽기 시점 포트 조회로 한다.
 */
@Entity
@Table(name = "shares")
public class Share {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 5)
    private ShareItemType itemType;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Share() {
    }

    public Share(Long ownerId, ShareItemType itemType, Long itemId) {
        this.ownerId = ownerId;
        this.itemType = itemType;
        this.itemId = itemId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public ShareItemType getItemType() { return itemType; }
    public Long getItemId() { return itemId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
