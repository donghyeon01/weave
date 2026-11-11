package app.weave.share.domain;

import app.weave.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 공유 답글 — parent_id NULL이 루트, 부모는 루트만 허용해 2단 상한 (앱 레벨 검증).
 * 삭제는 deleted_at tombstone — 자식 스레드를 보존한다.
 */
@Entity
@Table(name = "share_replies")
public class ShareReply extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "share_id", nullable = false)
    private Long shareId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected ShareReply() {
    }

    public ShareReply(Long shareId, Long parentId, Long authorId, String content) {
        this.shareId = shareId;
        this.parentId = parentId;
        this.authorId = authorId;
        this.content = content;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    /** tombstone 전이 — 행은 유지하고 조회 계층이 content를 마스킹한다. */
    public void markDeleted() {
        this.deletedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getShareId() { return shareId; }
    public Long getParentId() { return parentId; }
    public Long getAuthorId() { return authorId; }
    public String getContent() { return content; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
}
