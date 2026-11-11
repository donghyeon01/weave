package app.weave.scheduling.service;

import app.weave.scheduling.domain.Mode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 순수 추천 계산 — DB 의존 없음. 입력은 참여자별 busy 구간 맵이고,
 * 결과는 상위 3개 후보 (percent↓ → 날짜↑ → 시각↑).
 * allDay 이벤트는 로드 계층(SchedulingService)이 busy로 변환해 넘긴다 — endTime이 00:00이면
 * 그대로 배타 상한, 아니면 endTime 날짜+1일 00:00까지 [시작일 00:00, busyEnd)로 정규화된다.
 * windows는 {start,end} LocalTime 쌍 리스트 — 각 창을 독립 탐색하고 동일 슬롯은 dedupe한다.
 * 슬롯 길이는 slotMinutes, 탐색 간격은 stepMinutes (slotMinutes 생략 시 고정 4블록이라 둘 다 무시).
 */
public class ScheduleRecommender {

    /** slotMinutes 생략 시 TIME 모드의 고정 4블록 (계약 §3.8). */
    private static final int[][] FIXED_BLOCKS = {{10, 12}, {14, 16}, {18, 20}, {20, 22}};

    public record BusyInterval(LocalDateTime start, LocalDateTime end) {
        boolean overlaps(LocalDateTime s, LocalDateTime e) {
            return start.isBefore(e) && end.isAfter(s);
        }
    }

    public record Candidate(int rank, int percent, int availableCount, int totalCount,
            Slot slot) {
    }

    private record Scored(int percent, int availableCount, Slot slot,
            LocalDate dateKey, LocalTime timeKey) {
    }

    public List<Candidate> recommend(Mode mode, LocalDate rangeStart, LocalDate rangeEnd,
            List<LocalTime[]> windows, int stepMinutes, Integer slotMinutes,
            Integer durationDays, Map<Long, List<BusyInterval>> busyByUser) {
        // 공개 진입점 방어 — 유일 호출자(SchedulingService)가 400으로 거르지만,
        // 순수 계산기 계약 위반은 호출자 버그이므로 IllegalArgumentException으로 거부한다.
        if (mode == null || rangeStart == null || rangeEnd == null
                || rangeStart.isAfter(rangeEnd)) {
            throw new IllegalArgumentException("mode/range invalid");
        }
        if (busyByUser == null || busyByUser.isEmpty()) {
            throw new IllegalArgumentException("busyByUser must not be empty");
        }
        // 창·step은 slotMinutes 경로에서만 쓰인다 — 고정 블록(생략)은 창 없이 유효한 호출이다.
        if (mode == Mode.TIME && slotMinutes != null) {
            if (slotMinutes < 1 || stepMinutes < 1 || stepMinutes > slotMinutes
                    || windows == null || windows.isEmpty()
                    || windows.stream().anyMatch(
                            w -> w == null || w.length < 2
                                    || w[0] == null || w[1] == null
                                    || !w[1].isAfter(w[0]))) {
                throw new IllegalArgumentException("time window/slot/step invalid");
            }
        }
        if (mode == Mode.DATE && (durationDays == null || durationDays < 1)) {
            throw new IllegalArgumentException("durationDays invalid");
        }
        int total = busyByUser.size();
        List<Scored> scored = new ArrayList<>();

        if (mode == Mode.TIME) {
            for (LocalDate d = rangeStart; !d.isAfter(rangeEnd); d = d.plusDays(1)) {
                for (LocalDateTime[] se : slotsOfDay(d, windows, stepMinutes, slotMinutes)) {
                    int avail = countFree(busyByUser, se[0], se[1]);
                    if (avail > 0) {
                        scored.add(new Scored(avail * 100 / total, avail,
                                new Slot.Time(d, se[0].toLocalTime(), se[1].toLocalTime()),
                                d, se[0].toLocalTime()));
                    }
                }
            }
        } else {
            for (LocalDate d = rangeStart;
                    !d.plusDays(durationDays - 1L).isAfter(rangeEnd); d = d.plusDays(1)) {
                LocalDateTime start = d.atStartOfDay();
                LocalDateTime end = d.plusDays(durationDays).atStartOfDay();
                int avail = countFree(busyByUser, start, end);
                if (avail > 0) {
                    scored.add(new Scored(avail * 100 / total, avail,
                            new Slot.Days(d, d.plusDays(durationDays - 1L)), d, LocalTime.MIN));
                }
            }
        }

        List<Scored> top = scored.stream()
                .sorted(Comparator.comparingInt(Scored::percent).reversed()
                        .thenComparing(Scored::dateKey).thenComparing(Scored::timeKey))
                .limit(3)
                .toList();
        List<Candidate> out = new ArrayList<>(top.size());
        for (int i = 0; i < top.size(); i++) {
            Scored s = top.get(i);
            out.add(new Candidate(i + 1, s.percent(), s.availableCount(), total, s.slot()));
        }
        return out;
    }

    private int countFree(Map<Long, List<BusyInterval>> busyByUser, LocalDateTime s,
            LocalDateTime e) {
        int free = 0;
        for (List<BusyInterval> intervals : busyByUser.values()) {
            if (intervals.stream().noneMatch(iv -> iv.overlaps(s, e))) {
                free++;
            }
        }
        return free;
    }

    private List<LocalDateTime[]> slotsOfDay(LocalDate d, List<LocalTime[]> windows,
            int stepMinutes, Integer slotMinutes) {
        List<LocalDateTime[]> out = new ArrayList<>();
        if (slotMinutes == null) {
            for (int[] block : FIXED_BLOCKS) {
                out.add(new LocalDateTime[]{d.atTime(block[0], 0), d.atTime(block[1], 0)});
            }
            return out;
        }
        // 겹치는 창은 동일 슬롯을 만들 수 있으므로 (start,end) 기준 dedupe한다.
        Set<List<LocalDateTime>> seen = new LinkedHashSet<>();
        for (LocalTime[] window : windows) {
            // LocalTime.plusMinutes는 자정을 넘으면 wrap-around돼 isAfter(windowEnd)가 영구 거짓이 된다.
            // LocalDateTime으로 걸으면 자정 초과 시 dayEnd를 자연히 넘어 반드시 종료한다.
            LocalDateTime cursor = d.atTime(window[0]);
            LocalDateTime dayEnd = d.atTime(window[1]);
            for (LocalDateTime slotEnd = cursor.plusMinutes(slotMinutes);
                    !slotEnd.isAfter(dayEnd);
                    cursor = cursor.plusMinutes(stepMinutes),
                            slotEnd = cursor.plusMinutes(slotMinutes)) {
                if (seen.add(List.of(cursor, slotEnd))) {
                    out.add(new LocalDateTime[]{cursor, slotEnd});
                }
            }
        }
        return out;
    }
}
