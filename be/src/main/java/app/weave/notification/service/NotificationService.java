package app.weave.notification.service;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.notification.domain.Notification;
import app.weave.notification.domain.NotificationRepository;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(Long userId, Pageable pageable) {
        return repository.findPageByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public Notification markRead(Long userId, Long id) {
        Notification notification = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead();
        return notification;
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId, LocalDateTime.now());
    }
}
