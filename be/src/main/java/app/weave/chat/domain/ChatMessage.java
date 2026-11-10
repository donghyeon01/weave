package app.weave.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * append-only 메시지 행 — 수정·삭제 없음. client_message_id는 STOMP 재전송 멱등키로
 * UK(room_id, client_message_id)가 보장한다 (생략 시 NULL — 다중 NULL 허용).
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "client_message_id", length = 64)
    private String clientMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ChatMessage() {
    }

    public ChatMessage(Long roomId, Long senderId, String content, String clientMessageId) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.content = content;
        this.clientMessageId = clientMessageId;
    }

    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public Long getSenderId() { return senderId; }
    public String getContent() { return content; }
    public String getClientMessageId() { return clientMessageId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
