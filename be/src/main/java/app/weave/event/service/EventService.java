package app.weave.event.service;

import app.weave.auth.domain.User;
import app.weave.auth.domain.UserRepository;
import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.common.port.ProposalAccess;
import app.weave.common.port.ProposalRoster;
import app.weave.event.domain.Event;
import app.weave.event.domain.EventRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository repository;
    private final ProposalAccess proposalAccess;
    private final UserRepository userRepository;

    public EventService(EventRepository repository, ProposalAccess proposalAccess,
            UserRepository userRepository) {
        this.repository = repository;
        this.proposalAccess = proposalAccess;
        this.userRepository = userRepository;
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
                allDay, Event.Source.MANUAL, null, idempotencyKey));
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

    /**
     * 일정별 참여 친구 요약 — source=proposal이면 원 제안의 주최자·참여자 중 본인 제외.
     * 제안 로스터는 scheduling 소유라 ProposalAccess 포트로 조회한다 (계약 §3.4).
     */
    @Transactional(readOnly = true)
    public Map<Long, List<User>> participantsOf(Long me, List<Event> events) {
        List<Long> proposalIds = events.stream()
                .filter(e -> e.getSource() == Event.Source.PROPOSAL && e.getProposalId() != null)
                .map(Event::getProposalId).distinct().toList();
        if (proposalIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProposalRoster> rosters = proposalAccess.rosters(proposalIds);
        Map<Long, List<Long>> memberIdsByEvent = new HashMap<>();
        Set<Long> userIds = new HashSet<>();
        for (Event e : events) {
            if (e.getSource() != Event.Source.PROPOSAL || e.getProposalId() == null) {
                continue;
            }
            ProposalRoster roster = rosters.get(e.getProposalId());
            if (roster == null) {
                continue;
            }
            List<Long> memberIds = new ArrayList<>(
                    new LinkedHashSet<>(roster.participantUserIds()));
            memberIds.addFirst(roster.organizerId());
            memberIds = memberIds.stream().filter(uid -> !uid.equals(me)).distinct().toList();
            memberIdsByEvent.put(e.getId(), memberIds);
            userIds.addAll(memberIds);
        }
        Map<Long, User> users = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> users.put(u.getId(), u));
        Map<Long, List<User>> result = new HashMap<>();
        memberIdsByEvent.forEach((eventId, memberIds) -> result.put(eventId,
                memberIds.stream().map(users::get).filter(u -> u != null).toList()));
        return result;
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
