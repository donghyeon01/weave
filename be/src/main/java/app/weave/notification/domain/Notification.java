package app.weave.notification.domain;

import app.weave.notification.service.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 알림 저장 행 — 목록·배지의 유일 원천, STOMP push는 보조 (api-contract §3.9).
 * notifications 테이블은 created_at만 있어 BaseTimeEntity(updated_at)를 상속하지 않는다.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private Map<String, Object> payload;

    @Column(name = "read_at", columnDefinition = "DATETIME")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
    private LocalDateTime createdAt;

    protected Notification() {
    }

    public Notification(Long userId, NotificationType type, Map<String, Object> payload) {
        this.userId = userId;
        this.type = type;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
    }

    /** 최초 읽음 시각만 기록 — 재호출은 기존 readAt을 유지한다. */
    public void markRead() {
        if (this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public Map<String, Object> getPayload() { return payload; }
    public LocalDateTime getReadAt() { return readAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
