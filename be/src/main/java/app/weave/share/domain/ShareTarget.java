package app.weave.share.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import org.springframework.data.domain.Persistable;

/**
 * 공유 대상 행 — 열람 자격의 절반. 나머지 절반(현재 ACCEPTED 친구)은 읽기 시점에
 * FriendService로 검증한다. 친구 해제로 무효화된 행은 검증 경로에서 지연 정리된다.
 */
@Entity
@Table(name = "share_targets")
@IdClass(ShareTargetId.class)
public class ShareTarget implements Persistable<ShareTargetId> {

    /** 복합키 엔터티는 save가 merge로 판별해 SELECT+UPDATE — isNew로 배치 INSERT 경로를 연다. */
    @Transient
    private boolean isNew = true;

    @Id
    @Column(name = "share_id", nullable = false)
    private Long shareId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ShareTarget() {
    }

    public ShareTarget(Long shareId, Long userId) {
        this.shareId = shareId;
        this.userId = userId;
    }

    @PostPersist
    @PostLoad
    void markPersisted() {
        this.isNew = false;
    }

    @Override
    public ShareTargetId getId() {
        return new ShareTargetId(shareId, userId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public Long getShareId() { return shareId; }
    public Long getUserId() { return userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
