package app.weave.friend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 사용자 정의 친구 그룹 — GROUP 공개 범위의 멤버십 원천.
 * 사용자당 최대 3개는 앱 레벨 검증이다 (spec: idx만 두고 상한은 서비스에서 강제).
 * created_at만 감사 — 이름 변경 외 상태 전이가 없어 BaseTimeEntity를 쓰지 않는다.
 */
@Entity
@Table(name = "friend_groups")
public class FriendGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected FriendGroup() {
    }

    public FriendGroup(Long ownerId, String name) {
        this.ownerId = ownerId;
        this.name = name;
        this.createdAt = LocalDateTime.now();
    }

    public void rename(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
