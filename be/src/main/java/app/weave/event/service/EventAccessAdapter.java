package app.weave.event.service;

import app.weave.common.port.BusyBlock;
import app.weave.common.port.EventAccess;
import app.weave.common.port.EventRef;
import app.weave.common.port.SharedEvent;
import app.weave.event.domain.Event;
import app.weave.event.domain.EventRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** EventAccess 구현 — event 데이터의 읽기 전용 출구 (capability-map 조회 포트 규약). */
@Component
public class EventAccessAdapter implements EventAccess {

    private final EventRepository repository;

    public EventAccessAdapter(EventRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isOwnedBy(Long userId, Long eventId) {
        return repository.findByIdAndUserId(eventId, userId).isPresent();
    }

    @Override
    public List<BusyBlock> busyBlocksOf(Long userId, LocalDateTime start,
            LocalDateTime endExclusive) {
        // FREE는 시간 비점유(표시용) — busy 계산에서 제외. HARD·SOFT는 점유 블록으로 반환하되
        // 정책은 노출하지 않는다 (계약 §3.6). 정책별 분류가 필요한 소비자는 Event를 직접 읽는다.
        return repository.findByUserIdAndStartTimeLessThanAndEndTimeGreaterThan(
                userId, endExclusive, start).stream()
                .filter(e -> e.getBusyPolicy() != Event.BusyPolicy.FREE)
                .map(e -> new BusyBlock(e.getStartTime(), e.getEndTime()))
                .toList();
    }

    @Override
    public List<EventRef> proposalEventsAfter(Long userId, LocalDateTime from) {
        return repository.findByUserIdAndSourceAndStartTimeGreaterThanOrderByStartTimeAsc(
                userId, Event.Source.PROPOSAL, from).stream()
                .map(e -> new EventRef(e.getId(), e.getUserId(), e.getTitle(), e.getStartTime(),
                        e.getEndTime(), e.getProposalId()))
                .toList();
    }

    @Override
    public Optional<SharedEvent> eventDetailOf(Long ownerId, Long eventId) {
        return repository.findByIdAndUserId(eventId, ownerId)
                .map(e -> new SharedEvent(e.getId(), e.getUserId(), e.getTitle(),
                        e.getDescription(), e.getStartTime(), e.getEndTime(),
                        e.getLocation(), e.isAllDay()));
    }
}
