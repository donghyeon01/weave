package app.weave.notification.service;

/**
 * api-contract §3.7 알림 type — DB chk_notifications_type CHECK와 동일 문자열.
 * friend·scheduling이 service 패키지 경유로만 소비하므로 여기 둔다 (domain 직접 참조 금지).
 */
public enum NotificationType {
    FRIEND_REQUEST,
    FRIEND_ACCEPTED,
    PROPOSAL_RECEIVED,
    PROPOSAL_ALL_ACCEPTED,
    PROPOSAL_CONFIRMED,
    PROPOSAL_DECLINED,
    SHARED_ITEM,
    SHARE_REPLY
}
