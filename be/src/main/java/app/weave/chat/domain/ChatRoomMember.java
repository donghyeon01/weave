package app.weave.chat.domain;

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
 * 방 멤버 + 읽음 커서. 멤버십은 방 생성 시점 제안 로스터의 스냅샷 — 이후 제안 참여자가
 * 바뀌어도 갱신하지 않는다. last_read_message_id는 unread count의 원천 (api-contract §3.10).
 */
@Entity
@Table(name = "chat_room_members")
@IdClass(ChatRoomMemberId.class)
public class ChatRoomMember implements Persistable<ChatRoomMemberId> {

    /** 복합키 엔터티는 save가 merge로 판별해 SELECT+UPDATE — isNew로 배치 INSERT 경로를 연다. */
    @Transient
    private boolean isNew = true;

    @Id
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();

    protected ChatRoomMember() {
    }

    public ChatRoomMember(Long roomId, Long userId) {
        this.roomId = roomId;
        this.userId = userId;
    }

    @PostPersist
    @PostLoad
    void markPersisted() {
        this.isNew = false;
    }

    @Override
    public ChatRoomMemberId getId() {
        return new ChatRoomMemberId(roomId, userId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public Long getRoomId() { return roomId; }
    public Long getUserId() { return userId; }
    public Long getLastReadMessageId() { return lastReadMessageId; }
    public LocalDateTime getJoinedAt() { return joinedAt; }

    /** 커서 단조 증가 — 현재보다 작은 값은 무시하고 유지한다. */
    public void markRead(Long messageId) {
        if (lastReadMessageId == null || messageId > lastReadMessageId) {
            this.lastReadMessageId = messageId;
        }
    }
}
