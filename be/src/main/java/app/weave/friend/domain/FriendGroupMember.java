package app.weave.friend.domain;

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
 * 그룹 멤버 행 — 멤버는 현재 ACCEPTED 친구만 지정 가능하지만, 친구 해제로 행을
 * 지우지는 않는다. GROUP 공개 항목의 열람 판정은 멤버십 ∧ friendship이라 해제 즉시
 * 비가시·재친구 시 자동 복원된다 (spec §설계 결정).
 */
@Entity
@Table(name = "friend_group_members")
@IdClass(FriendGroupMemberId.class)
public class FriendGroupMember implements Persistable<FriendGroupMemberId> {

    /** 복합키 엔터티는 save가 merge로 판별해 SELECT+UPDATE — isNew로 배치 INSERT 경로를 연다. */
    @Transient
    private boolean isNew = true;

    @Id
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected FriendGroupMember() {
    }

    public FriendGroupMember(Long groupId, Long userId) {
        this.groupId = groupId;
        this.userId = userId;
    }

    @PostPersist
    @PostLoad
    void markPersisted() {
        this.isNew = false;
    }

    @Override
    public FriendGroupMemberId getId() {
        return new FriendGroupMemberId(groupId, userId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public Long getGroupId() { return groupId; }
    public Long getUserId() { return userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
