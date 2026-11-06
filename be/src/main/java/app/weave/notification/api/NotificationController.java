package app.weave.notification.api;

import app.weave.notification.domain.Notification;
import app.weave.notification.service.NotificationService;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationPageResponse list(@AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 계약 §1.5 — size 상한 100
        PageRequest pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 100));
        Page<Notification> result = notificationService.list(userId, pageable);
        return NotificationPageResponse.of(result, notificationService.unreadCount(userId));
    }

    @PatchMapping("/{id}/read")
    public ReadReceipt read(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        Notification n = notificationService.markRead(userId, id);
        return new ReadReceipt(n.getId(), n.getReadAt());
    }

    @PatchMapping("/read-all")
    public ReadAllResult readAll(@AuthenticationPrincipal Long userId) {
        return new ReadAllResult(notificationService.markAllRead(userId));
    }

    public record ReadReceipt(Long id, LocalDateTime readAt) {
    }

    public record ReadAllResult(int updatedCount) {
    }
}
