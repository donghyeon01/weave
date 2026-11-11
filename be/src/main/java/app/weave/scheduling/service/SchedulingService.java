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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
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
    private static final int DEFAULT_STEP_MINUTES = 30;

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
            LocalTime windowEnd, List<LocalTime[]> windows, Integer stepMinutes,
            Integer slotMinutes, Integer durationDays) {
        if (mode == null || rangeStart == null || rangeEnd == null
                || rangeStart.isAfter(rangeEnd)) {
            throw new BusinessException(ErrorCode.SCHEDULING_INVALID_INPUT);
        }
        if (mode == Mode.DATE && (durationDays == null || durationDays < 1)) {
            throw new BusinessException(ErrorCode.SCHEDULING_INVALID_INPUT);
        }
        int step = stepMinutes != null ? stepMinutes : DEFAULT_STEP_MINUTES;
        if (mode == Mode.TIME && slotMinutes != null) {
            // stepMinutes는 탐색 간격 — 슬롯 길이(slotMinutes)를 넘을 수 없다
            if (slotMinutes < 1 || step < 1 || step > slotMinutes) {
                throw new BusinessException(ErrorCode.SCHEDULING_INVALID_INPUT);
            }
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
            busyByUser.put(uid, new ArrayList<>());
        }
        for (Event e : events) {
            if (!e.isAllDay()) {
                busyByUser.get(e.getUserId())
                        .add(new BusyInterval(e.getStartTime(), e.getEndTime()));
                continue;
            }
            // allDay 저장 규약이 두 가지다 — DATE 확정 이벤트는 배타 상한 [.., 종료일 익일 00:00),
            // 레거시 수동 입력은 마지막 점유일 23:59:59. endTime이 자정이면 그대로 배타 상한,
            // 아니면 endTime 날짜의 익일 00:00까지가 busy다 (다일 종일의 모든 점유일을 막는다).
            LocalDateTime busyEnd = e.getEndTime().toLocalTime().equals(LocalTime.MIDNIGHT)
                    ? e.getEndTime()
                    : e.getEndTime().toLocalDate().plusDays(1).atStartOfDay();
            busyByUser.get(e.getUserId()).add(new BusyInterval(
                    e.getStartTime().toLocalDate().atStartOfDay(), busyEnd));
        }

        List<LocalTime[]> resolvedWindows;
        if (windows != null) {
            // 빈 windows는 명시적 오류 — "창 없음"과 "전체 기본 창"을 구분한다
            if (windows.isEmpty() || windows.stream().anyMatch(
                    w -> w == null || w.length < 2
                            || w[0] == null || w[1] == null
                            || !w[1].isAfter(w[0]))) {
                throw new BusinessException(ErrorCode.SCHEDULING_INVALID_INPUT);
            }
            resolvedWindows = windows;
        } else {
            LocalTime ws = windowStart != null ? windowStart : DEFAULT_WINDOW_START;
            LocalTime we = windowEnd != null ? windowEnd : DEFAULT_WINDOW_END;
            // 역창(종료≤시작)은 계약 미정의 — 빈 후보 반환 대신 명시적 400으로 거부
            if (!we.isAfter(ws)) {
                throw new BusinessException(ErrorCode.SCHEDULING_INVALID_INPUT);
            }
            resolvedWindows = List.<LocalTime[]>of(new LocalTime[]{ws, we});
        }

        return recommender.recommend(mode, rangeStart, rangeEnd,
                resolvedWindows, step, slotMinutes, durationDays, busyByUser);
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
