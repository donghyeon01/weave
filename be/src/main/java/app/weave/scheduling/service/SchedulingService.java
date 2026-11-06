package app.weave.scheduling.service;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.event.domain.Event;
import app.weave.event.domain.EventRepository;
import app.weave.friend.domain.Friendship;
import app.weave.friend.domain.Friendship.Status;
import app.weave.friend.domain.FriendshipRepository;
import app.weave.scheduling.domain.Mode;
import app.weave.scheduling.service.ScheduleRecommender.BusyInterval;
import app.weave.scheduling.service.ScheduleRecommender.Candidate;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulingService {

    private static final LocalTime DEFAULT_WINDOW_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_WINDOW_END = LocalTime.of(22, 0);

    private final FriendshipRepository friendshipRepository;
    private final EventRepository eventRepository;
    private final ScheduleRecommender recommender = new ScheduleRecommender();

    public SchedulingService(FriendshipRepository friendshipRepository,
            EventRepository eventRepository) {
        this.friendshipRepository = friendshipRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public List<Candidate> recommend(Long me, Mode mode, LocalDate rangeStart,
            LocalDate rangeEnd, List<Long> participantIds, LocalTime windowStart,
            LocalTime windowEnd, Integer slotMinutes, Integer durationDays) {
        if (mode == null || rangeStart == null || rangeEnd == null
                || rangeStart.isAfter(rangeEnd)) {
            throw new BusinessException(ErrorCode.SCHEDULING_INVALID_INPUT);
        }
        if (mode == Mode.DATE && (durationDays == null || durationDays < 1)) {
            throw new BusinessException(ErrorCode.SCHEDULING_INVALID_INPUT);
        }
        if (mode == Mode.TIME && slotMinutes != null && slotMinutes < 1) {
            throw new BusinessException(ErrorCode.SCHEDULING_INVALID_INPUT);
        }

        Set<Long> participants = resolveParticipants(me, participantIds);
        // 주최자도 참석자다 — 추천의 전체 인원은 친구들 + 나
        Set<Long> attendees = new HashSet<>(participants);
        attendees.add(me);

        LocalDate endExclusive = rangeEnd.plusDays(1);
        List<Event> events = eventRepository
                .findByUserIdInAndStartTimeLessThanAndEndTimeGreaterThan(
                        List.copyOf(attendees), endExclusive.atStartOfDay(),
                        rangeStart.atStartOfDay());

        Map<Long, List<BusyInterval>> busyByUser = new HashMap<>();
        for (Long uid : attendees) {
            busyByUser.put(uid, new java.util.ArrayList<>());
        }
        for (Event e : events) {
            // allDay는 해당 날짜 [00:00, 익일 00:00) 전체를 busy로 처리 (계획 명시)
            LocalDate day = e.getStartTime().toLocalDate();
            busyByUser.get(e.getUserId()).add(new BusyInterval(
                    e.isAllDay() ? day.atStartOfDay() : e.getStartTime(),
                    e.isAllDay() ? day.plusDays(1).atStartOfDay() : e.getEndTime()));
        }

        return recommender.recommend(mode, rangeStart, rangeEnd,
                windowStart != null ? windowStart : DEFAULT_WINDOW_START,
                windowEnd != null ? windowEnd : DEFAULT_WINDOW_END,
                slotMinutes, durationDays, busyByUser);
    }

    private Set<Long> resolveParticipants(Long me, List<Long> participantIds) {
        Set<Long> acceptedFriendIds = new HashSet<>(
                friendshipRepository.findAcceptedFriendships(me)
                        .stream().map(f -> f.counterpartyOf(me)).toList());
        if (participantIds == null || participantIds.isEmpty()) {
            // 생략 시 ACCEPTED 친구 전원 — 0명이면 추천 자체가 무의미 (계약 §3.8)
            if (acceptedFriendIds.isEmpty()) {
                throw new BusinessException(ErrorCode.SCHEDULING_INVALID_INPUT);
            }
            return acceptedFriendIds;
        }
        if (!acceptedFriendIds.containsAll(participantIds)) {
            throw new BusinessException(ErrorCode.SCHEDULING_NOT_FRIEND);
        }
        return new HashSet<>(participantIds);
    }
}
