package app.weave.common.port;

import java.time.LocalDateTime;

/** 공유 일정의 노출 계약 — participants는 계약상 노출 금지라 필드 자체가 없다. */
public record SharedEvent(Long id, Long userId, String title, String description,
        LocalDateTime startTime, LocalDateTime endTime, String location, boolean allDay) {
}
