package app.weave.common.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * event 모듈이 소유한 일정 데이터의 읽기 전용 포트.
 * 모듈 경계(단방향)상 event를 볼 수 없는 상류 모듈(diary·friend)이 이 포트로 조회한다 —
 * 구현은 event 패키지의 어댑터가 담당 (capability-map 조회 포트 규약).
 */
public interface EventAccess {

    /** 본인 소유 일정인지 — diaries.event_id 연결 검증용. */
    boolean isOwnedBy(Long userId, Long eventId);

    /** 지정 범위의 조율 불가 블록 — 친구 일정 미리보기(계약 §3.6). endExclusive는 배타 상한. */
    List<BusyBlock> busyBlocksOf(Long userId, LocalDateTime start, LocalDateTime endExclusive);

    /** source=PROPOSAL인 미래 일정 — 친구 nextEvent 조합용 (startTime 오름차순). */
    List<EventRef> proposalEventsAfter(Long userId, LocalDateTime from);

    /** 소유 검증 포함 일정 상세 — share의 항목 검증·노출용. 미존재·타인 소유는 empty. */
    Optional<SharedEvent> eventDetailOf(Long ownerId, Long eventId);
}
