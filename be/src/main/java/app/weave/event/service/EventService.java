package app.weave.event.service;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.event.domain.Event;
import app.weave.event.domain.EventRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository repository;

    public EventService(EventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Event create(Long userId, String title, String description, LocalDateTime start,
            LocalDateTime end, String location, boolean allDay, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Event> existing = repository
                    .findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        validateRange(start, end);
        return repository.save(new Event(userId, title, description, start, end, location,
                allDay, Event.Source.MANUAL, idempotencyKey));
    }

    @Transactional(readOnly = true)
    public List<Event> listAll(Long userId) {
        return repository.findByUserId(userId);
    }

    /** range의 API end는 "포함 끝 날짜" — 내부 쿼리는 배타 상한 end+1일 00:00으로 변환. */
    @Transactional(readOnly = true)
    public List<Event> findRange(Long userId, LocalDate start, LocalDate end) {
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endExclusive = end.plusDays(1).atStartOfDay();
        return repository.findByUserIdAndStartTimeLessThanAndEndTimeGreaterThan(
                userId, endExclusive, startAt);
    }

    @Transactional
    public Event update(Long userId, Long id, String title, String description,
            LocalDateTime start, LocalDateTime end, String location, Boolean allDay) {
        Event event = findOwned(userId, id);
        event.update(title, description, start, end, location, allDay);
        if (start != null || end != null) {
            validateRange(event.getStartTime(), event.getEndTime());
        }
        return event;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(findOwned(userId, id));
    }

    private void validateRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new BusinessException(ErrorCode.EVENT_INVALID_RANGE);
        }
    }

    /** 소유권 위반은 404로 응답 — 존재 여부를 새지 않는다. */
    private Event findOwned(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
    }
}
