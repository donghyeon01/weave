package app.weave.notification.api;

import app.weave.notification.domain.Notification;
import java.util.List;
import org.springframework.data.domain.Page;

/** api-contract §1.5 페이지 래퍼 + §3.7 최상위 unreadCount (벨 배지용). */
public record NotificationPageResponse(
        List<NotificationResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        long unreadCount) {

    public static NotificationPageResponse of(Page<Notification> page, long unreadCount) {
        return new NotificationPageResponse(
                page.getContent().stream().map(NotificationResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast(), unreadCount);
    }
}
