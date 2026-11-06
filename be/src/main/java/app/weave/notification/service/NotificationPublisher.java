package app.weave.notification.service;

import app.weave.notification.domain.Notification;
import app.weave.notification.domain.NotificationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 발행의 유일 진입점 — friend(Task 12)·scheduling(Task 14)이 주입해 호출한다.
 * INSERT는 호출자 트랜잭션 안에서 커밋되고, STOMP push는 커밋 후 이벤트로만 나간다 (plan Task 14 주석).
 */
@Service
public class NotificationPublisher {

    private final NotificationRepository repository;
    private final ApplicationEventPublisher events;

    public NotificationPublisher(NotificationRepository repository,
            ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Transactional
    public void publish(Long userId, NotificationPayload payload) {
        publish(userId, payload.type(), payload);
    }

    @Transactional
    public void publish(Long userId, NotificationType type, NotificationPayload payload) {
        if (payload.type() != type) {
            throw new IllegalArgumentException(
                    "payload " + payload.getClass().getSimpleName() + " is " + payload.type()
                            + ", not " + type);
        }
        Notification saved = repository.save(
                new Notification(userId, type, payload.toMap()));
        events.publishEvent(new NotificationCreatedEvent(saved));
    }
}
