package app.weave.common.port;

import java.time.LocalDateTime;

/** 교차 모듈 조회용 일정 요약 — proposalId로 원 제안 연결을 추적한다. */
public record EventRef(Long id, Long userId, String title, LocalDateTime startTime,
        LocalDateTime endTime, Long proposalId) {
}
