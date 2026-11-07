package app.weave.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.weave.scheduling.domain.Mode;
import app.weave.scheduling.service.ScheduleRecommender.BusyInterval;
import app.weave.scheduling.service.ScheduleRecommender.Candidate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScheduleRecommenderTest {

    private final ScheduleRecommender recommender = new ScheduleRecommender();
    private final LocalDate d1 = LocalDate.of(2026, 5, 12);
    private final LocalDate d2 = LocalDate.of(2026, 5, 13);

    private static BusyInterval busy(LocalDateTime s, LocalDateTime e) {
        return new BusyInterval(s, e);
    }

    @Test
    void 전원이_비어있는_슬롯은_percent_100이다() {
        Map<Long, List<BusyInterval>> busy = Map.of(1L, List.of(), 2L, List.of());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                LocalTime.of(9, 0), LocalTime.of(12, 0), 60, null, busy);

        assertThat(out).isNotEmpty();
        assertThat(out.get(0).percent()).isEqualTo(100);
        assertThat(out.get(0).availableCount()).isEqualTo(2);
        assertThat(out.get(0).totalCount()).isEqualTo(2);
        assertThat(out.get(0).rank()).isEqualTo(1);
    }

    @Test
    void 한_명이_겹치면_percent는_가능_비율이다() {
        // 4명 중 1명이 10-12 슬롯과 겹침 → 3/4 = 75%
        Map<Long, List<BusyInterval>> busy = Map.of(
                1L, List.of(), 2L, List.of(), 3L, List.of(),
                4L, List.of(busy(d1.atTime(10, 30), d1.atTime(11, 30))));
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                LocalTime.of(10, 0), LocalTime.of(12, 0), 120, null, busy);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).percent()).isEqualTo(75);
        assertThat(out.get(0).availableCount()).isEqualTo(3);
        assertThat(out.get(0).totalCount()).isEqualTo(4);
    }

    @Test
    void slotMinutes_생략_시_고정_4블록을_쓴다() {
        Map<Long, List<BusyInterval>> busy = Map.of(1L, List.of());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                null, null, null, null, busy);

        assertThat(out).hasSize(3); // 상위 3개
        // 고정 블록의 첫 슬롯은 10-12
        Slot.Time first = (Slot.Time) out.get(0).slot();
        assertThat(first.startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(first.endTime()).isEqualTo(LocalTime.of(12, 0));
        // 4블록 모두 percent 동률이면 시각 오름차순 — 10시→14시→18시
        assertThat(((Slot.Time) out.get(1).slot()).startTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(((Slot.Time) out.get(2).slot()).startTime()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void DATE_모드는_연속_일_구간을_슬라이딩한다() {
        // 2명, durationDays=2 — 한 명이 13일에 일정 → 12-13 구간은 50%, 14-15 구간은 100%
        Map<Long, List<BusyInterval>> busy = Map.of(
                1L, List.of(),
                2L, List.of(busy(d2.atTime(10, 0), d2.atTime(11, 0))));
        List<Candidate> out = recommender.recommend(Mode.DATE, d1, d1.plusDays(3),
                null, null, null, 2, busy);

        assertThat(out.get(0).percent()).isEqualTo(100);
        Slot.Days first = (Slot.Days) out.get(0).slot();
        // 전원 빈 첫 구간은 5/14~5/15 (5/12~13과 5/13~14는 2번의 13일 일정에 걸림)
        assertThat(first.startDate()).isEqualTo(LocalDate.of(2026, 5, 14));
        assertThat(first.endDate()).isEqualTo(LocalDate.of(2026, 5, 15));
    }

    @Test
    void 정렬은_percent가_날짜보다_우선이다() {
        // 2명 — 둘째 날은 전원 빔(100), 첫날은 1명 겹침(50). 날짜가 늦어도 percent 높은 쪽이 1위
        Map<Long, List<BusyInterval>> busy = Map.of(
                1L, List.of(busy(d1.atTime(9, 0), d1.atTime(10, 0))),
                2L, List.of());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d2,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 120, null, busy);

        assertThat(out).hasSize(2);
        assertThat(out.get(0).percent()).isEqualTo(100);
        assertThat(((Slot.Time) out.get(0).slot()).date()).isEqualTo(d2);
        assertThat(out.get(1).percent()).isEqualTo(50);
        assertThat(((Slot.Time) out.get(1).slot()).date()).isEqualTo(d1);
    }

    @Test
    void 모든_슬롯이_막히면_빈_배열이다() {
        // 1명이 종일 busy → 가능 슬롯 없음
        Map<Long, List<BusyInterval>> busy = Map.of(
                1L, List.of(busy(d1.atStartOfDay(), d1.plusDays(1).atStartOfDay())));
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                LocalTime.of(9, 0), LocalTime.of(22, 0), 60, null, busy);

        assertThat(out).isEmpty();
    }

    @Test
    void 스텝이_자정을_넘어도_종료한다() {
        // windowEnd 도달 전 t+step이 자정을 넘어 wrap되면 LocalTime 비교는 영구 거짓 —
        // 과거 무한 루프로 OOM을 일으킨 입력 (화면 기본값 창 12~22시·120분과 동형).
        Map<Long, List<BusyInterval>> busy = Map.of(1L, List.of());
        List<Candidate> out = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                java.time.Duration.ofSeconds(10),
                () -> recommender.recommend(Mode.TIME, d1, d1,
                        LocalTime.of(22, 0), LocalTime.of(23, 59), 120, null, busy));

        // 22:00 슬롯만 생성 (22:00+120은 익일 → 창 초과로 종료)
        assertThat(out).isEmpty();
    }

    @Test
    void 창이_자정을_걸치지_않으면_끝까지_슬롯을_만든다() {
        // 12~22시 2시간 스텝 → 12·14·16·18·20시 5슬롯 (20시 슬롯은 정확히 22:00에 맞닿아 포함).
        // LocalTime 비교라면 22:00 스텝이 익일 00:00으로 wrap돼 무한 루프였다.
        Map<Long, List<BusyInterval>> busy = Map.of(1L, List.of());
        List<Candidate> out = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                java.time.Duration.ofSeconds(10),
                () -> recommender.recommend(Mode.TIME, d1, d1,
                        LocalTime.of(12, 0), LocalTime.of(22, 0), 120, null, busy));

        assertThat(out).hasSize(3); // 상위 3개만 반환
    }
}
