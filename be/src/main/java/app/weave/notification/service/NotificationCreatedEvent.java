package app.weave.notification.service;

import app.weave.notification.domain.Notification;

/**
 * NOTIFICATIONS INSERT 직후 발행되는 도메인 이벤트.
 * AFTER_COMMIT 리스너가 잡아 STOMP push로 변환한다 — push는 저장과 무관한 보조 채널.
 */
public record NotificationCreatedEvent(Notification notification) {
}
