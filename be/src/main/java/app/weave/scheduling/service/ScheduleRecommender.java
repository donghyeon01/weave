package app.weave.scheduling.service;

import app.weave.common.port.AvailabilityBlockInfo;
import app.weave.common.port.AvailabilityScopeInfo;
import app.weave.common.port.PreferredWindowInfo;
import app.weave.scheduling.domain.Mode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

    /**
     * 참여자별 추천 입력 — scopes는 요청 mode 일치·COMPLETED 분만 조립해 넘긴다.
     * preferredWindows는 preferenceScore 계산 입력 — 가용성 판정에는 관여하지 않는다.
     */
    public record UserScheduleInput(List<BusyInterval> hard, List<BusyInterval> soft,
            List<AvailabilityScopeInfo> scopes, boolean calendarInference,
            List<PreferredWindowInfo> preferredWindows) {
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

    /** 정례 후보의 회차별 집계 — percent는 floor(100×|Aₖ|/N). */
    public record OccurrenceInfo(LocalDate date, int percent, int availableCount,
            Breakdown breakdown) {
    }

    /**
     * 추천 후보 — 신규 필드(preferenceScore~occurrences)는 정례·선호 계산이 없으면 null.
     * 단일 추천은 occurrenceCount·minOccurrencePercent·occurrences가 모두 null.
     */
    public record Candidate(int rank, int percent, int availableCount, int totalCount,
            Breakdown breakdown, Slot slot,
            Integer preferenceScore, Integer preferenceRespondentCount,
            Integer occurrenceCount, Integer minOccurrencePercent,
            List<OccurrenceInfo> occurrences) {
    }

    /** 정렬 내부 표현 — minOccurrenceAvail은 정례 정렬의 2번째 키(노출 필드 아님). */
    private record Scored(int percent, int availableCount, Breakdown breakdown, Slot slot,
            LocalDate dateKey, LocalTime timeKey,
            Integer preferenceScore, Integer preferenceRespondentCount,
            Integer occurrenceCount, Integer minOccurrencePercent,
            List<OccurrenceInfo> occurrences, int minOccurrenceAvail) {
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
            List<LocalTime[]> pairs = timePairs(windows, stepMinutes, slotMinutes);
            for (LocalDate d = rangeStart; !d.isAfter(rangeEnd); d = d.plusDays(1)) {
                for (LocalTime[] pair : pairs) {
                    Breakdown b = breakdown(inputByUser, d.atTime(pair[0]),
                            d.atTime(pair[1]), mode);
                    int avail = b.direct() + b.calendarFree();
                    if (avail > 0) {
                        Pref pref = preferenceSingle(inputByUser, d, pair[0], pair[1]);
                        scored.add(new Scored(avail * 100 / total, avail, b,
                                new Slot.Time(d, pair[0], pair[1]),
                                d, pair[0], pref.score(), pref.count(),
                                null, null, null, 0));
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
                    // DATE 모드는 시간대 개념이 없어 preferenceScore는 항상 null
                    scored.add(new Scored(avail * 100 / total, avail, b,
                            new Slot.Days(d, d.plusDays(durationDays - 1L)),
                            d, LocalTime.MIN, null, null, null, null, null, 0));
                }
            }
        }

        // 정렬: availableCount↓ → preferenceScore↓(null 최하) → 날짜↑ → 시각↑ (계약 §3.8)
        List<Scored> top = scored.stream()
                .sorted(Comparator.comparingInt(Scored::availableCount).reversed()
                        .thenComparing(Scored::preferenceScore,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Scored::dateKey).thenComparing(Scored::timeKey))
                .limit(3)
                .toList();
        List<Candidate> out = new ArrayList<>(top.size());
        for (int i = 0; i < top.size(); i++) {
            Scored s = top.get(i);
            out.add(toCandidate(i + 1, s, total));
        }
        return out;
    }

    /**
     * TIME 전용 정례 추천 — occurrenceDates(정확히 count개, 호출자 전개 완료)에 같은
     * 시각 슬롯을 적용해 시리즈 집계한다. 사용자 시리즈 분류 = 회차별 분류의 최악값
     * (unavailable > conditional > unknown > calendarFree > direct).
     */
    public List<Candidate> recommendRecurring(List<LocalTime[]> windows, int stepMinutes,
            Integer slotMinutes, List<LocalDate> occurrenceDates,
            Map<Long, UserScheduleInput> inputByUser) {
        if (occurrenceDates == null || occurrenceDates.isEmpty()) {
            throw new IllegalArgumentException("occurrenceDates must not be empty");
        }
        if (inputByUser == null || inputByUser.isEmpty()) {
            throw new IllegalArgumentException("inputByUser must not be empty");
        }
        if (slotMinutes != null) {
            if (slotMinutes < 1 || stepMinutes < 1 || stepMinutes > slotMinutes
                    || windows == null || windows.isEmpty()
                    || windows.stream().anyMatch(
                            w -> w == null || w.length < 2
                                    || w[0] == null || w[1] == null
                                    || !w[1].isAfter(w[0]))) {
                throw new IllegalArgumentException("time window/slot/step invalid");
            }
        }
        int total = inputByUser.size();
        List<LocalTime[]> pairs = timePairs(windows, stepMinutes, slotMinutes);
        List<Scored> scored = new ArrayList<>();

        for (LocalTime[] pair : pairs) {
            Map<Long, Integer> seriesClass = new HashMap<>();
            List<OccurrenceInfo> occs = new ArrayList<>(occurrenceDates.size());
            int minOccAvail = Integer.MAX_VALUE;
            for (LocalDate d : occurrenceDates) {
                LocalDateTime s = d.atTime(pair[0]);
                LocalDateTime e = d.atTime(pair[1]);
                int[] c = new int[5];
                int occAvail = 0;
                for (Map.Entry<Long, UserScheduleInput> en : inputByUser.entrySet()) {
                    int cls = classify(en.getValue(), s, e, Mode.TIME);
                    c[cls]++;
                    // 시리즈 분류 = 회차별 분류의 최악값 — 인덱스가 작을수록 나쁘다
                    seriesClass.merge(en.getKey(), cls, Math::min);
                    if (cls == DIRECT || cls == CALENDAR_FREE) {
                        occAvail++;
                    }
                }
                occs.add(new OccurrenceInfo(d, occAvail * 100 / total, occAvail,
                        new Breakdown(c[DIRECT], c[CALENDAR_FREE], c[CONDITIONAL],
                                c[UNKNOWN], c[UNAVAILABLE])));
                minOccAvail = Math.min(minOccAvail, occAvail);
            }
            int[] sc = new int[5];
            for (int cls : seriesClass.values()) {
                sc[cls]++;
            }
            Breakdown sb = new Breakdown(sc[DIRECT], sc[CALENDAR_FREE], sc[CONDITIONAL],
                    sc[UNKNOWN], sc[UNAVAILABLE]);
            int avail = sb.direct() + sb.calendarFree();
            if (avail > 0) {
                int minPct = occs.stream().mapToInt(OccurrenceInfo::percent).min()
                        .orElse(0);
                Pref pref = preferenceRecurring(inputByUser, occurrenceDates,
                        pair[0], pair[1]);
                scored.add(new Scored(avail * 100 / total, avail, sb,
                        new Slot.Time(occurrenceDates.get(0), pair[0], pair[1]),
                        occurrenceDates.get(0), pair[0],
                        pref.score(), pref.count(),
                        occurrenceDates.size(), minPct, occs, minOccAvail));
            }
        }

        // 정렬: availableCount↓ → 최저 회차 availableCount↓ → preferenceScore↓(null 최하) → 시각↑
        // (dateKey는 모든 후보가 같은 occurrenceDates를 공유해 의미 없음 — 계약 §3.8)
        List<Scored> top = scored.stream()
                .sorted(Comparator.comparingInt(Scored::availableCount).reversed()
                        .thenComparing(Comparator.comparingInt(Scored::minOccurrenceAvail)
                                .reversed())
                        .thenComparing(Scored::preferenceScore,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Scored::timeKey))
                .limit(3)
                .toList();
        List<Candidate> out = new ArrayList<>(top.size());
        for (int i = 0; i < top.size(); i++) {
            Scored s = top.get(i);
            out.add(toCandidate(i + 1, s, total));
        }
        return out;
    }

    private static Candidate toCandidate(int rank, Scored s, int total) {
        return new Candidate(rank, s.percent(), s.availableCount(), total,
                s.breakdown(), s.slot(), s.preferenceScore(),
                s.preferenceRespondentCount(), s.occurrenceCount(),
                s.minOccurrencePercent(), s.occurrences());
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

    /**
     * 날짜 무관 시각 슬롯 열거 — (start,end) LocalTime 쌍 집합을 한 번만 생성한다
     * (정례는 같은 시각 쌍을 모든 회차에 적용). 겹치는 창의 동일 슬롯은 dedupe한다.
     * slotMinutes 생략 시 FIXED_BLOCKS의 시각 쌍.
     */
    private List<LocalTime[]> timePairs(List<LocalTime[]> windows, int stepMinutes,
            Integer slotMinutes) {
        List<LocalTime[]> out = new ArrayList<>();
        if (slotMinutes == null) {
            for (int[] block : FIXED_BLOCKS) {
                out.add(new LocalTime[]{LocalTime.of(block[0], 0),
                        LocalTime.of(block[1], 0)});
            }
            return out;
        }
        // 겹치는 창은 동일 슬롯을 만들 수 있으므로 (start,end) 기준 dedupe한다.
        Set<List<LocalTime>> seen = new LinkedHashSet<>();
        // LocalTime.plusMinutes는 자정을 넘으면 wrap-around돼 isAfter(windowEnd)가 영구 거짓이 된다.
        // 기준일의 LocalDateTime으로 걸으면 자정 초과 시 dayEnd를 자연히 넘어 반드시 종료한다.
        LocalDate ref = LocalDate.of(2000, 1, 1);
        for (LocalTime[] window : windows) {
            LocalDateTime cursor = ref.atTime(window[0]);
            LocalDateTime dayEnd = ref.atTime(window[1]);
            for (LocalDateTime slotEnd = cursor.plusMinutes(slotMinutes);
                    !slotEnd.isAfter(dayEnd);
                    cursor = cursor.plusMinutes(stepMinutes),
                            slotEnd = cursor.plusMinutes(slotMinutes)) {
                List<LocalTime> pair = List.of(cursor.toLocalTime(),
                        slotEnd.toLocalTime());
                if (seen.add(pair)) {
                    out.add(new LocalTime[]{pair.get(0), pair.get(1)});
                }
            }
        }
        return out;
    }

    /** preferenceScore 계산 결과 — 등록 참여자 0이면 둘 다 null. */
    private record Pref(Integer score, Integer count) {
        private static final Pref NONE = new Pref(null, null);
    }

    /** 사용자의 특정 요일(ISO 1~7) 선호 윈도우 — 미등록이면 빈 리스트. */
    private static List<PreferredWindowInfo> windowsOf(UserScheduleInput u,
            int dayOfWeek) {
        List<PreferredWindowInfo> pw = u.preferredWindows();
        if (pw == null) {
            return List.of();
        }
        List<PreferredWindowInfo> out = new ArrayList<>();
        for (PreferredWindowInfo w : pw) {
            if (w.dayOfWeek() == dayOfWeek) {
                out.add(w);
            }
        }
        return out;
    }

    /**
     * 슬롯 [s,e)와 해당 요일 선호 윈도우 합집합의 겹침 비율(0~1).
     * 윈도우는 [start,end) — 교집 구간을 모아 정렬·병합(방어적 중첩 허용) 후 합산한다.
     */
    private static double coverageRatio(List<PreferredWindowInfo> dayWindows,
            LocalTime s, LocalTime e) {
        List<LocalTime[]> hits = new ArrayList<>();
        for (PreferredWindowInfo w : dayWindows) {
            LocalTime a = w.startTime().isAfter(s) ? w.startTime() : s;
            LocalTime b = w.endTime().isBefore(e) ? w.endTime() : e;
            if (a.isBefore(b)) {
                hits.add(new LocalTime[]{a, b});
            }
        }
        hits.sort(Comparator.comparing(h -> h[0]));
        long covered = 0;
        LocalTime cs = null, ce = null;
        for (LocalTime[] h : hits) {
            if (cs == null) {
                cs = h[0];
                ce = h[1];
            } else if (!h[0].isAfter(ce)) {
                if (h[1].isAfter(ce)) {
                    ce = h[1];
                }
            } else {
                covered += Duration.between(cs, ce).toMinutes();
                cs = h[0];
                ce = h[1];
            }
        }
        if (cs != null) {
            covered += Duration.between(cs, ce).toMinutes();
        }
        long slot = Duration.between(s, e).toMinutes();
        return slot <= 0 ? 0 : Math.min(1.0, (double) covered / slot);
    }

    /**
     * 단일 후보의 preferenceScore — 해당 요일에 선호 윈도우를 등록한 참여자의
     * 적합도 평균×100(반올림). 등록+불일치는 0점으로 분모에 포함, 미등록은 분모 제외.
     */
    private Pref preferenceSingle(Map<Long, UserScheduleInput> inputByUser,
            LocalDate d, LocalTime s, LocalTime e) {
        int dow = d.getDayOfWeek().getValue();
        double sum = 0;
        int n = 0;
        for (UserScheduleInput u : inputByUser.values()) {
            List<PreferredWindowInfo> ws = windowsOf(u, dow);
            if (ws.isEmpty()) {
                continue;
            }
            sum += coverageRatio(ws, s, e);
            n++;
        }
        return n == 0 ? Pref.NONE
                : new Pref((int) Math.round(sum / n * 100), n);
    }

    /**
     * 정례 후보의 preferenceScore — 사용자별로 "해당 회차 요일에 윈도우 등록된 회차"의
     * 적합도 평균을 내고, 그 사용자 평균들끼리 평균×100. 등록 회차 0인 사용자는 제외.
     */
    private Pref preferenceRecurring(Map<Long, UserScheduleInput> inputByUser,
            List<LocalDate> dates, LocalTime s, LocalTime e) {
        double userSum = 0;
        int n = 0;
        for (UserScheduleInput u : inputByUser.values()) {
            double occSum = 0;
            int m = 0;
            for (LocalDate d : dates) {
                List<PreferredWindowInfo> ws =
                        windowsOf(u, d.getDayOfWeek().getValue());
                if (ws.isEmpty()) {
                    continue;
                }
                occSum += coverageRatio(ws, s, e);
                m++;
            }
            if (m > 0) {
                userSum += occSum / m;
                n++;
            }
        }
        return n == 0 ? Pref.NONE
                : new Pref((int) Math.round(userSum / n * 100), n);
    }
}
