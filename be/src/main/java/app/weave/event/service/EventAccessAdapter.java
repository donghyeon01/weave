package app.weave.event.service;

import app.weave.common.port.BusyBlock;
import app.weave.common.port.EventAccess;
import app.weave.common.port.EventRef;
import app.weave.event.domain.Event;
import app.weave.event.domain.EventRepository;
import java.time.LocalDateTime;
import java.util.List;
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
        return repository.findByUserIdAndStartTimeLessThanAndEndTimeGreaterThan(
                userId, endExclusive, start).stream()
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
}
