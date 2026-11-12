package app.weave.scheduling.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import app.weave.common.port.AvailabilityBlockInfo;
import app.weave.common.port.AvailabilityScopeInfo;
import app.weave.common.port.PreferredWindowInfo;
import app.weave.scheduling.domain.Mode;
import app.weave.scheduling.service.RecurrenceExpander.Spec;
import app.weave.scheduling.service.ScheduleRecommender.BusyInterval;
import app.weave.scheduling.service.ScheduleRecommender.UserScheduleInput;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * 탐색 부하 실측 스모크 테스트 — 성능 게이트가 아니라 회귀 감지용이다.
 * 최악 입력(정례 12회차·4주 간격·11명·각 수백 개 구간)을 합성해
 * {@code recommendRecurring}·{@code recommend}의 실행 시간 분포를 출력하고,
 * p95에 넉넉한 상한(10s)만 걸어 병적 회귀만 잡는다. CI 플레이크 방지를 위해
 * 실제 예산(500ms 게이트 판정)은 측정 보고서에서 다룬다.
 */
class ScheduleRecommenderLoadTest {

    private static final int WARMUP = 3;
    private static final int RUNS = 20;

    private final ScheduleRecommender recommender = new ScheduleRecommender();

    // 2026-05-04는 월요일 — interval 4·byDay 화요일·count 12 → 수평선 ~308일
    private final LocalDate horizonStart = LocalDate.of(2026, 5, 4);
    private final List<LocalDate> occurrenceDates = RecurrenceExpander.expand(
            horizonStart, Spec.of("WEEKLY", 4, List.of(2), 12));
    private final LocalDate horizonEnd =
            occurrenceDates.get(occurrenceDates.size() - 1).plusDays(7);

    private final List<LocalTime[]> windows =
            List.<LocalTime[]>of(new LocalTime[]{LocalTime.of(9, 0), LocalTime.of(22, 0)});

    @Test
    void 정례_최악_입력의_recommendRecurring_실행시간을_측정한다() {
        Map<Long, UserScheduleInput> inputByUser = buildParticipants(11, 42L);

        for (int i = 0; i < WARMUP; i++) {
            recommender.recommendRecurring(windows, 15, 30, occurrenceDates,
                    inputByUser);
        }
        List<Long> times = new ArrayList<>(RUNS);
        for (int i = 0; i < RUNS; i++) {
            long t0 = System.nanoTime();
            recommender.recommendRecurring(windows, 15, 30, occurrenceDates,
                    inputByUser);
            times.add((System.nanoTime() - t0) / 1_000_000);
        }
        Stats stats = Stats.of(times);
        System.out.printf(
                "[load] recommendRecurring occurrences=%d pairs≈52 users=%d "
                        + "runs=%d min=%dms p50=%dms p95=%dms max=%dms%n",
                occurrenceDates.size(), inputByUser.size(), stats.count,
                stats.min, stats.p50, stats.p95, stats.max);

        assertTrue(stats.p95 < 10_000,
                "recommendRecurring p95가 상한 10s를 초과: " + stats.p95 + "ms");
    }

    @Test
    void 단일_추천_30일_범위의_recommend_실행시간을_측정한다() {
        LocalDate rangeStart = horizonStart;
        LocalDate rangeEnd = horizonStart.plusDays(29);
        Map<Long, UserScheduleInput> inputByUser = buildParticipants(11, 42L);

        for (int i = 0; i < WARMUP; i++) {
            recommender.recommend(Mode.TIME, rangeStart, rangeEnd, windows, 15,
                    30, null, inputByUser);
        }
        List<Long> times = new ArrayList<>(RUNS);
        for (int i = 0; i < RUNS; i++) {
            long t0 = System.nanoTime();
            recommender.recommend(Mode.TIME, rangeStart, rangeEnd, windows, 15,
                    30, null, inputByUser);
            times.add((System.nanoTime() - t0) / 1_000_000);
        }
        Stats stats = Stats.of(times);
        System.out.printf(
                "[load] recommend range=30d users=%d runs=%d "
                        + "min=%dms p50=%dms p95=%dms max=%dms%n",
                inputByUser.size(), stats.count, stats.min, stats.p50, stats.p95,
                stats.max);

        assertTrue(stats.p95 < 10_000,
                "recommend p95가 상한 10s를 초과: " + stats.p95 + "ms");
    }

    /**
     * 사용자 1명분의 합성 입력 — 수평선을 3등분한 COMPLETED scope 각각에
     * 선언 블록 20개 + 수평선 전체에 hard 100개·soft 100개 + 요일별 선호 윈도우.
     * 시드 고정으로 실행 간 입력을 동일하게 유지한다.
     */
    private Map<Long, UserScheduleInput> buildParticipants(int users, long seed) {
        Random rnd = new Random(seed);
        Map<Long, UserScheduleInput> out = new HashMap<>();
        for (long u = 1; u <= users; u++) {
            List<BusyInterval> hard = randomIntervals(rnd, 100);
            List<BusyInterval> soft = randomIntervals(rnd, 100);
            List<AvailabilityScopeInfo> scopes = buildScopes(rnd);
            List<PreferredWindowInfo> prefs = List.of(
                    new PreferredWindowInfo(2, LocalTime.of(10, 0),
                            LocalTime.of(13, 0)),
                    new PreferredWindowInfo(2, LocalTime.of(19, 0),
                            LocalTime.of(21, 0)),
                    new PreferredWindowInfo(4, LocalTime.of(14, 0),
                            LocalTime.of(18, 0)));
            out.put(u, new UserScheduleInput(hard, soft, scopes, true, prefs));
        }
        return out;
    }

    /** 수평선 3등분 scope — 각 scope 안에 선언 블록 20개(각 1~2시간). */
    private List<AvailabilityScopeInfo> buildScopes(Random rnd) {
        long span = horizonEnd.toEpochDay() - horizonStart.toEpochDay() + 1;
        long third = span / 3;
        List<AvailabilityScopeInfo> scopes = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            LocalDate rs = horizonStart.plusDays(third * k);
            LocalDate re = k == 2 ? horizonEnd
                    : horizonStart.plusDays(third * (k + 1) - 1);
            List<AvailabilityBlockInfo> blocks = new ArrayList<>(20);
            for (int b = 0; b < 20; b++) {
                LocalDate day = rs.plusDays(
                        rnd.nextInt((int) (re.toEpochDay() - rs.toEpochDay() + 1)));
                int startHour = 9 + rnd.nextInt(12);
                int endHour = Math.min(22, startHour + 1 + rnd.nextInt(2));
                blocks.add(new AvailabilityBlockInfo(null, day.atTime(startHour, 0),
                        day.atTime(endHour, 0)));
            }
            scopes.add(new AvailabilityScopeInfo(rs, re, blocks));
        }
        return scopes;
    }

    /** 수평선 전체에 무작위 busy 구간 n개 — 각 30~90분. */
    private List<BusyInterval> randomIntervals(Random rnd, int n) {
        long days = horizonEnd.toEpochDay() - horizonStart.toEpochDay() + 1;
        List<BusyInterval> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            LocalDate day = horizonStart.plusDays(rnd.nextInt((int) days));
            LocalDateTime s = day.atTime(rnd.nextInt(24), rnd.nextInt(60));
            out.add(new BusyInterval(s, s.plusMinutes(30 + rnd.nextInt(61))));
        }
        return out;
    }

    private record Stats(int count, long min, long p50, long p95, long max) {
        static Stats of(List<Long> ms) {
            List<Long> sorted = ms.stream().sorted().toList();
            return new Stats(sorted.size(), sorted.get(0),
                    sorted.get((int) Math.ceil(0.50 * sorted.size()) - 1),
                    sorted.get((int) Math.ceil(0.95 * sorted.size()) - 1),
                    sorted.get(sorted.size() - 1));
        }
    }
}
