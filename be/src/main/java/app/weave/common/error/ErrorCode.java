package app.weave.common.error;

/**
 * api-contract §2 에러 코드 레지스트리 전량.
 * code 문자열은 배포 후 불변 — 클라이언트가 이 값으로만 분기한다.
 */
public enum ErrorCode {

    COMMON_VALIDATION("COMMON-VALIDATION", 400, "입력값이 올바르지 않습니다."),
    COMMON_MALFORMED("COMMON-MALFORMED", 400, "요청 본문을 해석할 수 없습니다."),
    AUTH_UNAUTHORIZED("AUTH-UNAUTHORIZED", 401, "인증이 필요합니다."),
    AUTH_KAKAO_EXCHANGE_FAILED("AUTH-KAKAO-EXCHANGE-FAILED", 401, "카카오 로그인에 실패했습니다."),
    AUTH_REFRESH_INVALID("AUTH-REFRESH-INVALID", 401, "세션이 유효하지 않습니다. 다시 로그인해주세요."),
    AUTH_REFRESH_REUSED("AUTH-REFRESH-REUSED", 401, "비정상적인 세션 사용이 감지되어 로그아웃되었습니다."),
    AUTH_UA_MISMATCH("AUTH-UA-MISMATCH", 401, "기기 정보가 일치하지 않습니다. 다시 로그인해주세요."),
    AUTH_SESSION_EVICTED("AUTH-SESSION-EVICTED", 401, "다른 기기에서 로그인하여 로그아웃되었습니다."),
    DEMO_RATE_LIMITED("DEMO-RATE-LIMITED", 429, "데모 로그인 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    ACCESS_DENIED("ACCESS-DENIED", 403, "권한이 없습니다."),
    USER_NOT_FOUND("USER-NOT-FOUND", 404, "사용자를 찾을 수 없습니다."),
    TASK_NOT_FOUND("TASK-NOT-FOUND", 404, "할 일을 찾을 수 없습니다."),
    EVENT_NOT_FOUND("EVENT-NOT-FOUND", 404, "일정을 찾을 수 없습니다."),
    EVENT_INVALID_RANGE("EVENT-INVALID-RANGE", 400, "시작 시각이 종료 시각보다 빨라야 합니다."),
    DIARY_NOT_FOUND("DIARY-NOT-FOUND", 404, "해당 날짜의 기록이 없습니다."),
    DIARY_DUPLICATE("DIARY-DUPLICATE", 409, "해당 날짜에 이미 기록이 있습니다."),
    DIARY_PHOTO_NOT_FOUND("DIARY-PHOTO-NOT-FOUND", 404, "사진을 찾을 수 없습니다."),
    DIARY_PHOTO_LIMIT("DIARY-PHOTO-LIMIT", 409, "사진은 일기당 최대 4장입니다."),
    DIARY_PHOTO_TYPE("DIARY-PHOTO-TYPE", 400, "JPEG·PNG·WebP만 업로드할 수 있습니다."),
    DIARY_PHOTO_SIZE("DIARY-PHOTO-SIZE", 400, "사진은 5MB를 초과할 수 없습니다."),
    FRIEND_SELF_REQUEST("FRIEND-SELF-REQUEST", 400, "본인에게는 친구 요청을 보낼 수 없습니다."),
    FRIEND_DUPLICATE_REQUEST("FRIEND-DUPLICATE-REQUEST", 409, "이미 친구 요청을 보낸 사용자입니다."),
    FRIEND_ALREADY("FRIEND-ALREADY", 409, "이미 친구인 사용자입니다."),
    FRIENDSHIP_NOT_FOUND("FRIENDSHIP-NOT-FOUND", 404, "친구 관계를 찾을 수 없습니다."),
    NOTIFICATION_NOT_FOUND("NOTIFICATION-NOT-FOUND", 404, "알림을 찾을 수 없습니다."),
    SCHEDULING_INVALID_INPUT("SCHEDULING-INVALID-INPUT", 400, "조율 조건이 올바르지 않습니다."),
    SCHEDULING_NOT_FRIEND("SCHEDULING-NOT-FRIEND", 400, "친구가 아닌 사용자가 포함되어 있습니다."),
    PROPOSAL_NOT_FOUND("PROPOSAL-NOT-FOUND", 404, "제안을 찾을 수 없습니다."),
    PROPOSAL_NOT_PENDING("PROPOSAL-NOT-PENDING", 409, "이미 처리된 제안입니다."),
    PROPOSAL_NOT_AWAITING_CONFIRM("PROPOSAL-NOT-AWAITING-CONFIRM", 409,
            "확정 대기 중인 제안이 아닙니다."),
    PROPOSAL_NOT_PARTICIPANT("PROPOSAL-NOT-PARTICIPANT", 403, "제안의 참여자가 아닙니다."),
    CHAT_ROOM_NOT_FOUND("CHAT-ROOM-NOT-FOUND", 404, "채팅방을 찾을 수 없습니다."),
    CHAT_NOT_MEMBER("CHAT-NOT-MEMBER", 404, "채팅방 멤버가 아닙니다."),
    CHAT_ROOM_CLOSED("CHAT-ROOM-CLOSED", 409, "종료된 채팅방입니다."),
    CHAT_MESSAGE_INVALID("CHAT-MESSAGE-INVALID", 400, "메시지가 올바르지 않습니다."),
    INTERNAL_ERROR("INTERNAL-ERROR", 500, "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");

    private final String code;
    private final int status;
    private final String message;

    ErrorCode(String code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
