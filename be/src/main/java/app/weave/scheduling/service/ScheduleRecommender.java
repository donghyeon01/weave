package app.weave.scheduling.service;

import app.weave.scheduling.domain.Mode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 순수 추천 계산 — DB 의존 없음. 입력은 참여자별 busy 구간 맵이고,
 * 결과는 상위 3개 후보 (percent↓ → 날짜↑ → 시각↑).
 * allDay 이벤트는 로드 계층(SchedulingService)이 [당일 00:00, 익일 00:00) busy로 변환해 넘긴다.
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
            LocalTime windowStart, LocalTime windowEnd, Integer slotMinutes,
            Integer durationDays, Map<Long, List<BusyInterval>> busyByUser) {
        int total = busyByUser.size();
        List<Scored> scored = new ArrayList<>();

        if (mode == Mode.TIME) {
            for (LocalDate d = rangeStart; !d.isAfter(rangeEnd); d = d.plusDays(1)) {
                for (LocalDateTime[] se : slotsOfDay(d, windowStart, windowEnd, slotMinutes)) {
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

    private List<LocalDateTime[]> slotsOfDay(LocalDate d, LocalTime windowStart,
            LocalTime windowEnd, Integer slotMinutes) {
        List<LocalDateTime[]> out = new ArrayList<>();
        if (slotMinutes == null) {
            for (int[] block : FIXED_BLOCKS) {
                out.add(new LocalDateTime[]{d.atTime(block[0], 0), d.atTime(block[1], 0)});
            }
            return out;
        }
        for (LocalTime t = windowStart; !t.plusMinutes(slotMinutes).isAfter(windowEnd);
                t = t.plusMinutes(slotMinutes)) {
            out.add(new LocalDateTime[]{d.atTime(t), d.atTime(t.plusMinutes(slotMinutes))});
        }
        return out;
    }
}
