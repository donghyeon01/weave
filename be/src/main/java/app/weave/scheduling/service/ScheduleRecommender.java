package app.weave.scheduling.service;

import app.weave.common.port.AvailabilityBlockInfo;
import app.weave.common.port.AvailabilityScopeInfo;
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
import java.util.TreeSet;

/**
 * 순수 추천 계산 — DB 의존 없음. 입력은 참여자별 {@link UserScheduleInput}이고,
 * 결과는 상위 3개 후보 (percent↓ → 날짜↑ → 시각↑).
 * 슬롯별 사용자 판정은 spec §추천 판정 규칙의 5분류:
 * HARD 겹침 또는 완료 범위 미선택 → unavailable · SOFT 겹침 → conditional ·
 * 전체 완료 선언 커버 → direct · 선언+추론 혼합/순수 추론 커버 → calendarFree · 나머지 → unknown.
 * availableCount = direct + calendarFree · percent = floor(100×availableCount/totalCount).
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

    /** 참여자별 추천 입력 — scopes는 요청 mode 일치·COMPLETED 분만 조립해 넘긴다. */
    public record UserScheduleInput(List<BusyInterval> hard, List<BusyInterval> soft,
            List<AvailabilityScopeInfo> scopes, boolean calendarInference) {
    }

    /** 분류 버킷 인덱스 — 슬롯별 사용자 정확히 한 분류 (spec 판정표 순서). */
    private static final int UNAVAILABLE = 0;
    private static final int CONDITIONAL = 1;
    private static final int UNKNOWN = 2;
    private static final int CALENDAR_FREE = 3;
    private static final int DIRECT = 4;

    /** 후보별 5분류 인원수 — 합계는 항상 totalCount. */
    public record Breakdown(int direct, int calendarFree, int conditional, int unknown,
            int unavailable) {
    }

    public record Candidate(int rank, int percent, int availableCount, int totalCount,
            Breakdown breakdown, Slot slot) {
    }

    private record Scored(int percent, int availableCount, Breakdown breakdown, Slot slot,
            LocalDate dateKey, LocalTime timeKey) {
    }

    public List<Candidate> recommend(Mode mode, LocalDate rangeStart, LocalDate rangeEnd,
            List<LocalTime[]> windows, int stepMinutes, Integer slotMinutes,
            Integer durationDays, Map<Long, UserScheduleInput> inputByUser) {
        // 공개 진입점 방어 — 유일 호출자(SchedulingService)가 400으로 거르지만,
        // 순수 계산기 계약 위반은 호출자 버그이므로 IllegalArgumentException으로 거부한다.
        if (mode == null || rangeStart == null || rangeEnd == null
                || rangeStart.isAfter(rangeEnd)) {
            throw new IllegalArgumentException("mode/range invalid");
        }
        if (inputByUser == null || inputByUser.isEmpty()) {
            throw new IllegalArgumentException("inputByUser must not be empty");
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
        int total = inputByUser.size();
        List<Scored> scored = new ArrayList<>();

        if (mode == Mode.TIME) {
            for (LocalDate d = rangeStart; !d.isAfter(rangeEnd); d = d.plusDays(1)) {
                for (LocalDateTime[] se : slotsOfDay(d, windows, stepMinutes, slotMinutes)) {
                    Breakdown b = breakdown(inputByUser, se[0], se[1], mode);
                    int avail = b.direct() + b.calendarFree();
                    if (avail > 0) {
                        scored.add(new Scored(avail * 100 / total, avail, b,
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
                Breakdown b = breakdown(inputByUser, start, end, mode);
                int avail = b.direct() + b.calendarFree();
                if (avail > 0) {
                    scored.add(new Scored(avail * 100 / total, avail, b,
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
            out.add(new Candidate(i + 1, s.percent(), s.availableCount(), total,
                    s.breakdown(), s.slot()));
        }
        return out;
    }

    private Breakdown breakdown(Map<Long, UserScheduleInput> inputByUser,
            LocalDateTime s, LocalDateTime e, Mode mode) {
        int[] c = new int[5];
        for (UserScheduleInput u : inputByUser.values()) {
            c[classify(u, s, e, mode)]++;
        }
        return new Breakdown(c[DIRECT], c[CALENDAR_FREE], c[CONDITIONAL], c[UNKNOWN],
                c[UNAVAILABLE]);
    }

    /**
     * 슬롯 s=[s,e)에 대한 사용자 한 명의 분류 (spec 판정표 우선순위 그대로):
     * 1) HARD 겹침 → unavailable  2) 완료 범위 내 미선택 구간 존재 → unavailable
     * 3) SOFT 겹침 → conditional  4) 슬롯 전체 선언 커버 → direct
     * 5) 선언+범위 밖 추론으로 커버 → calendarFree  6) 나머지 → unknown.
     */
    private int classify(UserScheduleInput u, LocalDateTime s, LocalDateTime e, Mode mode) {
        for (BusyInterval iv : u.hard()) {
            if (iv.overlaps(s, e)) {
                return UNAVAILABLE;
            }
        }
        Coverage cov = coverage(u, s, e, mode);
        if (cov.hasUnselected) {
            return UNAVAILABLE;
        }
        for (BusyInterval iv : u.soft()) {
            if (iv.overlaps(s, e)) {
                return CONDITIONAL;
            }
        }
        if (!cov.hasOutside) {
            return DIRECT;
        }
        return u.calendarInference() ? CALENDAR_FREE : UNKNOWN;
    }

    /** s의 구간 분해 결과 — 범위 내 미선택 존재 / 범위 밖 구간 존재. */
    private record Coverage(boolean hasUnselected, boolean hasOutside) {
    }

    private Coverage coverage(UserScheduleInput u, LocalDateTime s, LocalDateTime e,
            Mode mode) {
        List<AvailabilityScopeInfo> scopes =
                u.scopes() != null ? u.scopes() : List.of();
        if (mode == Mode.DATE) {
            return dateCoverage(scopes, s.toLocalDate(), e.toLocalDate().minusDays(1));
        }
        return timeCoverage(scopes, s, e);
    }

    /**
     * DATE: 슬롯의 각 날짜가 완료 scope에 들어가면 해당 날짜 블록(blockDate)이 필요하다.
     * 어떤 날짜도 두 scope에 동시에 속할 수 없다 (활성 범위 중첩 금지).
     */
    private Coverage dateCoverage(List<AvailabilityScopeInfo> scopes,
            LocalDate first, LocalDate last) {
        boolean hasUnselected = false, hasOutside = false;
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            AvailabilityScopeInfo containing = null;
            for (AvailabilityScopeInfo sc : scopes) {
                if (!d.isBefore(sc.rangeStart()) && !d.isAfter(sc.rangeEnd())) {
                    containing = sc;
                    break;
                }
            }
            if (containing == null) {
                hasOutside = true;
                continue;
            }
            LocalDate day = d;
            boolean declared = containing.blocks().stream()
                    .anyMatch(b -> day.equals(b.blockDate()));
            if (!declared) {
                hasUnselected = true;
            }
        }
        return new Coverage(hasUnselected, hasOutside);
    }

    /**
     * TIME: s를 scope 경계·블록 경계로 쪼개 각 조각이 "선언 / 범위 내 미선택 / 범위 밖"
     * 중 무엇인지 표시한다. 범위 내 미선택이 1조각이라도 있으면 unavailable이고,
     * 범위 밖 조각은 추론 여부로 calendarFree·unknown을 나눈다.
     */
    private Coverage timeCoverage(List<AvailabilityScopeInfo> scopes,
            LocalDateTime s, LocalDateTime e) {
        // clippedRanges[k]는 hitScopes[k]의 슬롯 교집 범위 — 인덱스 정합 유지.
        List<BusyInterval> clippedRanges = new ArrayList<>();
        List<AvailabilityScopeInfo> hitScopes = new ArrayList<>();
        TreeSet<LocalDateTime> cuts = new TreeSet<>(List.of(s, e));
        for (AvailabilityScopeInfo sc : scopes) {
            LocalDateTime a = sc.rangeStart().atStartOfDay();
            LocalDateTime b = sc.rangeEnd().plusDays(1).atStartOfDay();
            if (a.isBefore(e) && b.isAfter(s)) {
                clippedRanges.add(new BusyInterval(max(a, s), min(b, e)));
                hitScopes.add(sc);
                cuts.add(max(a, s));
                cuts.add(min(b, e));
            }
            for (AvailabilityBlockInfo bl : sc.blocks()) {
                if (bl.startAt() == null || bl.endAt() == null) {
                    continue;
                }
                if (bl.startAt().isBefore(e) && bl.endAt().isAfter(s)) {
                    cuts.add(max(bl.startAt(), s));
                    cuts.add(min(bl.endAt(), e));
                }
            }
        }
        boolean hasUnselected = false, hasOutside = false;
        List<LocalDateTime> pts = new ArrayList<>(cuts);
        for (int i = 0; i + 1 < pts.size(); i++) {
            LocalDateTime a = pts.get(i);
            LocalDateTime b = pts.get(i + 1);
            if (!b.isAfter(a)) {
                continue;
            }
            // 조각을 포함하는 scope를 찾고 그 scope의 블록만 선언으로 인정한다 —
            // 활성 scope 비중첩 불변 덕에 containing scope는 최대 1개다.
            AvailabilityScopeInfo containing = null;
            for (int k = 0; k < clippedRanges.size(); k++) {
                BusyInterval r = clippedRanges.get(k);
                if (!r.start().isAfter(a) && !r.end().isBefore(b)) {
                    containing = hitScopes.get(k);
                    break;
                }
            }
            if (containing == null) {
                hasOutside = true;
                continue;
            }
            boolean declared = false;
            for (AvailabilityBlockInfo bl : containing.blocks()) {
                if (bl.startAt() != null && bl.endAt() != null
                        && !bl.startAt().isAfter(a) && !bl.endAt().isBefore(b)) {
                    declared = true;
                    break;
                }
            }
            if (!declared) {
                hasUnselected = true;
            }
        }
        return new Coverage(hasUnselected, hasOutside);
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
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
