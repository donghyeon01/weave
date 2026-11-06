package app.weave.notification.api;

import app.weave.notification.domain.Notification;
import app.weave.notification.service.NotificationType;
import java.time.LocalDateTime;
import java.util.Map;

/** api-contract §3.7 공통 응답 — REST 목록 요소와 STOMP push 본문이 같은 스키마다. */
public record NotificationResponse(
        Long id,
        NotificationType type,
        Map<String, Object> payload,
        LocalDateTime readAt,
        LocalDateTime createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getPayload(),
                n.getReadAt(), n.getCreatedAt());
    }
}
