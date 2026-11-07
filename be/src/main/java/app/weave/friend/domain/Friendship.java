package app.weave.friend.domain;

import app.weave.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "friendships")
public class Friendship extends BaseTimeEntity {

    public enum Status {
        PENDING, ACCEPTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 요청 발신자. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 요청 수신자. */
    @Column(name = "friend_id", nullable = false)
    private Long friendId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    /** 요청 메시지 — PENDING 요청에서 주로 사용 (api-contract §3.6). */
    @Column(length = 100)
    private String message;

    protected Friendship() {
    }

    public Friendship(Long userId, Long friendId, String message) {
        this.userId = userId;
        this.friendId = friendId;
        this.message = message;
        this.status = Status.PENDING;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getFriendId() { return friendId; }
    public Status getStatus() { return status; }
    public String getMessage() { return message; }

    public void accept() {
        this.status = Status.ACCEPTED;
    }

    /** me가 어느 쪽이든 상대방 id를 반환한다. */
    public Long counterpartyOf(Long me) {
        return userId.equals(me) ? friendId : userId;
    }
}
