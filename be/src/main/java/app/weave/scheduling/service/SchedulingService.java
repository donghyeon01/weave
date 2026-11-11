package app.weave.scheduling.service;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.common.port.AvailabilityAccess;
import app.weave.common.port.AvailabilityMode;
import app.weave.common.port.UserAvailability;
import app.weave.event.domain.Event;
import app.weave.event.domain.EventRepository;
import app.weave.friend.domain.Friendship;
import app.weave.friend.domain.Friendship.Status;
import app.weave.friend.domain.FriendshipRepository;
import app.weave.scheduling.domain.Mode;
import app.weave.scheduling.service.ScheduleRecommender.BusyInterval;
import app.weave.scheduling.service.ScheduleRecommender.Candidate;
import app.weave.scheduling.service.ScheduleRecommender.UserScheduleInput;
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
    private final AvailabilityAccess availabilityAccess;
    private final ScheduleRecommender recommender = new ScheduleRecommender();

    public SchedulingService(FriendshipRepository friendshipRepository,
            EventRepository eventRepository, AvailabilityAccess availabilityAccess) {
        this.friendshipRepository = friendshipRepository;
        this.eventRepository = eventRepository;
        this.availabilityAccess = availabilityAccess;
    }

    /** 추천 응답 — participantIds는 주최자 제외 계산 시점 스냅샷, computedAt은 ISO instant (계약 §3.8). */
    public record RecommendResult(List<Candidate> candidates,
            List<Long> participantIds, String computedAt) {
    }

    @Transactional(readOnly = true)
    public RecommendResult recommend(Long me, Mode mode, LocalDate rangeStart,
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

        // busyPolicy별 busy 분리 — HARD는 선언도 못 이기는 불가, SOFT는 conditional.
        // FREE는 시간 비점유(표시용)라 busy 해석에서 제외한다.
        Map<Long, List<BusyInterval>> hardByUser = new HashMap<>();
        Map<Long, List<BusyInterval>> softByUser = new HashMap<>();
        for (Long uid : attendees) {
            hardByUser.put(uid, new ArrayList<>());
            softByUser.put(uid, new ArrayList<>());
        }
        for (Event e : events) {
            if (e.getBusyPolicy() == Event.BusyPolicy.FREE) {
                continue;
            }
            BusyInterval iv;
            if (!e.isAllDay()) {
                iv = new BusyInterval(e.getStartTime(), e.getEndTime());
            } else {
                // allDay 저장 규약이 두 가지다 — DATE 확정 이벤트는 배타 상한 [.., 종료일 익일 00:00),
                // 레거시 수동 입력은 마지막 점유일 23:59:59. endTime이 자정이면 그대로 배타 상한,
                // 아니면 endTime 날짜의 익일 00:00까지가 busy다 (다일 종일의 모든 점유일을 막는다).
                LocalDateTime busyEnd = e.getEndTime().toLocalTime().equals(LocalTime.MIDNIGHT)
                        ? e.getEndTime()
                        : e.getEndTime().toLocalDate().plusDays(1).atStartOfDay();
                iv = new BusyInterval(e.getStartTime().toLocalDate().atStartOfDay(), busyEnd);
            }
            (e.getBusyPolicy() == Event.BusyPolicy.SOFT ? softByUser : hardByUser)
                    .get(e.getUserId()).add(iv);
        }

        // COMPLETED scope는 요청 mode와 같은 것만 판정 근거다 (spec 모드 스코핑)
        Map<Long, UserAvailability> availabilities = availabilityAccess.availabilityOf(
                List.copyOf(attendees), AvailabilityMode.valueOf(mode.name()),
                rangeStart, rangeEnd);

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

        Map<Long, UserScheduleInput> inputByUser = new HashMap<>();
        for (Long uid : attendees) {
            UserAvailability av = availabilities.getOrDefault(uid,
                    new UserAvailability(List.of(), false));
            inputByUser.put(uid, new UserScheduleInput(hardByUser.get(uid),
                    softByUser.get(uid), av.scopes(), av.useCalendarInference()));
        }

        List<Candidate> candidates = recommender.recommend(mode, rangeStart, rangeEnd,
                resolvedWindows, step, slotMinutes, durationDays, inputByUser);
        // 참여자 스냅샷은 주최자 제외 — 정렬해 결정적 순서로 반환한다.
        // computedAt은 서버 로컬 시각 — 다른 DTO의 datetime 필드와 같은 직렬화 규약 (계약 §3.8).
        return new RecommendResult(candidates,
                participants.stream().sorted().toList(), LocalDateTime.now().toString());
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
