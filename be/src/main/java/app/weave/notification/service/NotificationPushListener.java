package app.weave.notification.service;

import app.weave.notification.api.NotificationResponse;
import app.weave.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋 후 STOMP push — 계약 §3.9 "/user/queue/notifications", 본문은 NotificationResponse와 동형.
 */
@Component
public class NotificationPushListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationPushListener.class);

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationPushListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * AFTER_COMMIT — push 실패가 INSERT를 롤백하지 않도록 커밋 이후에만 보낸다.
     * 예외는 삼키고 로그만 남긴다: afterCommit 콜백의 예외는 트랜잭션 호출자에게 전파돼
     * 저장은 됐는데 요청이 500처럼 보이는 상황을 만들 수 있기 때문.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCreated(NotificationCreatedEvent event) {
        Notification n = event.notification();
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(n.getUserId()),
                    "/queue/notifications",
                    NotificationResponse.from(n));
        } catch (Exception e) {
            log.warn("알림 push 실패 notificationId={}", n.getId(), e);
        }
    }
}
