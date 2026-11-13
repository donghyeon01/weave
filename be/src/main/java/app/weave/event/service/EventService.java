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
import java.time.LocalTime;
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
            LocalDateTime end, String location, boolean allDay, Event.BusyPolicy busyPolicy,
            String idempotencyKey) {
        // 본인 user 행 잠금 — confirmProposal의 참석자 잠금(id 오름차순)과 같은 규약으로
        // HARD 일정 쓰기를 직렬화한다 (spec §Confirm 재검증).
        userRepository.findByIdForUpdate(userId);
        if (idempotencyKey != null) {
            Optional<Event> existing = repository
                    .findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        if (allDay && start != null && end != null) {
            // allDay 저장 규약 [start일 00:00, end일+1 00:00) 배타 상한 — 시각은 무시하고 날짜만 취한다
            start = start.toLocalDate().atStartOfDay();
            end = end.toLocalDate().plusDays(1).atStartOfDay();
        }
        validateRange(start, end);
        return repository.save(new Event(userId, title, description, start, end, location,
                allDay, Event.Source.MANUAL, null, busyPolicy, idempotencyKey));
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
            LocalDateTime start, LocalDateTime end, String location, Boolean allDay,
            Event.BusyPolicy busyPolicy) {
        // findOwned보다 user 잠금이 먼저 — 잠금과 조회 사이에 confirm이 끼어들지 못하게 한다
        userRepository.findByIdForUpdate(userId);
        Event event = findOwned(userId, id);
        boolean wasAllDay = event.isAllDay();
        event.update(title, description, start, end, location, allDay, busyPolicy);
        // 정규화 대상: allDay로 전환되거나, allDay 상태에서 시각이 바뀔 때.
        // 이미 allDay인 일정에 allDay:true만 재전송하면 재정규화로 하루 밀리므로 제외한다.
        if (event.isAllDay() && (start != null || end != null
                || (Boolean.TRUE.equals(allDay) && !wasAllDay))) {
            LocalDateTime normStart = event.getStartTime().toLocalDate().atStartOfDay();
            // 저장된 end가 자정이면 이미 배타 상한이라 그대로, 아니면 end 날짜+1일 00:00.
            // 사용자가 보낸 end는 "포함 마지막 날짜"라 항상 +1일한다.
            LocalDateTime effEnd = end != null ? end : event.getEndTime();
            LocalDateTime normEnd = end == null
                    && effEnd.toLocalTime().equals(LocalTime.MIDNIGHT)
                    ? effEnd
                    : effEnd.toLocalDate().plusDays(1).atStartOfDay();
            validateRange(normStart, normEnd);
            event.update(null, null, normStart, normEnd, null, null, null);
        } else if (!event.isAllDay() && wasAllDay) {
            // allDay 해제: 저장된 배타 상한을 마지막 점유일 23:59:59로 환원한다.
            // 사용자가 end를 직접 보냈으면 그 값을 쓰고, 레거시 23:59:59 저장값은 그대로 통과.
            if (end == null && event.getEndTime().toLocalTime()
                    .equals(LocalTime.MIDNIGHT)) {
                event.update(null, null, null,
                        event.getEndTime().minusDays(1)
                                .with(LocalTime.of(23, 59, 59)),
                        null, null, null);
            }
            validateRange(event.getStartTime(), event.getEndTime());
        } else if (start != null || end != null) {
            validateRange(event.getStartTime(), event.getEndTime());
        }
        return event;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        userRepository.findByIdForUpdate(userId);
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
