package app.weave.share.domain;

import app.weave.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 항목별 공개 설정 1행 — uk_shares_item(owner_id, item_type, item_id)이 항목당 1행을 강제하고
 * PUT upsert가 유일한 쓰기 경로다. 행이 없는 항목은 비공개와 동치.
 * item_id는 diary·event 테이블로의 논리 참조 — 존재·소유 검증은 읽기 시점 포트 조회로 한다.
 * group_id는 GROUP일 때만 채운다 — 그룹 삭제 시 DB가 SET NULL로 비우고 서비스가 비공개 취급한다.
 */
@Entity
@Table(name = "shares")
public class Share extends BaseTimeEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 7)
    private ShareVisibility visibility;

    @Column(name = "group_id")
    private Long groupId;

    protected Share() {
    }

    public Share(Long ownerId, ShareItemType itemType, Long itemId,
            ShareVisibility visibility, Long groupId) {
        this.ownerId = ownerId;
        this.itemType = itemType;
        this.itemId = itemId;
        this.visibility = visibility;
        this.groupId = groupId;
    }

    /** upsert 갱신 — GROUP이 아니면 groupId를 비워 설정과 참조를 일치시킨다. */
    public void updateVisibility(ShareVisibility visibility, Long groupId) {
        this.visibility = visibility;
        this.groupId = visibility == ShareVisibility.GROUP ? groupId : null;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public ShareItemType getItemType() { return itemType; }
    public Long getItemId() { return itemId; }
    public ShareVisibility getVisibility() { return visibility; }
    public Long getGroupId() { return groupId; }
}
