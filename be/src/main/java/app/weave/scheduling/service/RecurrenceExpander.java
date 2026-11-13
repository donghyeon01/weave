package app.weave.scheduling.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 정례 조율 회차 날짜 전개 — 순수 계산 유틸 (DB·Spring 의존 없음).
 * 회차 채택 규칙: 첫 매칭 날짜가 속한 ISO week(월요일 시작)를 anchor로 잡고,
 * byDay 매칭 날짜 중 (weekBucket - anchorBucket) % interval == 0인 날짜만 채택한다.
 * weekBucket은 epochDay/7이 아니라 "그 날짜의 ISO 월요일 epochDay / 7"이다 —
 * 달력상 같은 주로 보이는 일요일과 다음날 월요일이 서로 다른 버킷이 되어야 하므로
 * 날짜 자체를 7로 나누면 요일 조합에 따라 잘못 묶인다 (spec D10, Astra OQ1).
 */
public final class RecurrenceExpander {

    private RecurrenceExpander() {
    }

    /** freq는 WEEKLY만 지원 — MVP 범위 (spec D10). */
    public record Spec(String freq, int interval, Set<Integer> byDays, int count) {

        /** 검증 통과 시 Spec, 위반 시 IllegalArgumentException — 호출자가 400으로 변환. */
        public static Spec of(String freq, Integer interval, List<Integer> byDays, Integer count) {
            if (!"WEEKLY".equals(freq)) {
                throw new IllegalArgumentException("지원하지 않는 반복 주기입니다: " + freq);
            }
            if (interval == null || interval < 1 || interval > 4) {
                throw new IllegalArgumentException("반복 간격은 1~4 사이여야 합니다: " + interval);
            }
            if (byDays == null || byDays.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("반복 요일은 비어 있지 않은 1~7 집합이어야 합니다.");
            }
            TreeSet<Integer> days = new TreeSet<>(byDays);
            if (days.isEmpty() || days.first() < 1 || days.last() > 7) {
                throw new IllegalArgumentException("반복 요일은 비어 있지 않은 1~7 집합이어야 합니다: " + byDays);
            }
            if (count == null || count < 2 || count > 12) {
                throw new IllegalArgumentException("회차 수는 2~12 사이여야 합니다: " + count);
            }
            return new Spec(freq, interval, Collections.unmodifiableSet(days), count);
        }
    }

    /** firstOnOrAfter 이후 byDay 매칭 날짜부터 count개 — 수평선 없음(제안용). */
    public static List<LocalDate> expand(LocalDate firstOnOrAfter, Spec spec) {
        LocalDate first = firstMatch(firstOnOrAfter, spec.byDays());
        long anchor = weekBucket(first);
        List<LocalDate> dates = new ArrayList<>(spec.count());
        LocalDate d = first;
        while (dates.size() < spec.count()) {
            if (isOccurrence(d, spec, anchor)) {
                dates.add(d);
            }
            d = d.plusDays(1);
        }
        return dates;
    }

    /** [from,to] 안에서 정확히 count개 — 부족하면 IllegalArgumentException(추천용). */
    public static List<LocalDate> expandWithin(LocalDate from, LocalDate to, Spec spec) {
        LocalDate first = firstMatch(from, spec.byDays());
        long anchor = weekBucket(first);
        List<LocalDate> dates = new ArrayList<>(spec.count());
        LocalDate d = first;
        while (!d.isAfter(to) && dates.size() < spec.count()) {
            if (isOccurrence(d, spec, anchor)) {
                dates.add(d);
            }
            d = d.plusDays(1);
        }
        if (dates.size() < spec.count()) {
            throw new IllegalArgumentException(
                    "범위 안에서 요청한 회차 수를 채울 수 없습니다: " + spec.count() + "개 필요, " + dates.size() + "개");
        }
        return dates;
    }

    private static LocalDate firstMatch(LocalDate from, Set<Integer> byDays) {
        LocalDate d = from;
        while (!byDays.contains(d.getDayOfWeek().getValue())) {
            d = d.plusDays(1);
        }
        return d;
    }

    private static boolean isOccurrence(LocalDate d, Spec spec, long anchorBucket) {
        return spec.byDays().contains(d.getDayOfWeek().getValue())
                && (weekBucket(d) - anchorBucket) % spec.interval() == 0;
    }

    /** ISO week(월요일 시작) 버킷 — 해당 날짜가 속한 주의 월요일 기준. */
    private static long weekBucket(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay() / 7;
    }
}
