package app.weave.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.weave.scheduling.service.RecurrenceExpander.Spec;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecurrenceExpanderTest {

    private static Spec spec(int interval, List<Integer> byDays, int count) {
        return Spec.of("WEEKLY", interval, byDays, count);
    }

    private static LocalDate dayOfWeek(DayOfWeek dow) {
        return LocalDate.of(2026, 5, 11).with(dow);
    }

    @Test
    void interval1_단일요일_연속주() {
        LocalDate monday = dayOfWeek(DayOfWeek.MONDAY);
        List<LocalDate> dates = RecurrenceExpander.expand(monday, spec(1, List.of(2), 4));

        assertThat(dates).containsExactly(
                monday.plusDays(1), monday.plusDays(8),
                monday.plusDays(15), monday.plusDays(22));
    }

    @Test
    void interval2_복수요일_같은주_묶음() {
        LocalDate monday = dayOfWeek(DayOfWeek.MONDAY);
        List<LocalDate> dates = RecurrenceExpander.expand(monday, spec(2, List.of(2, 4), 6));

        assertThat(dates).containsExactly(
                monday.plusDays(1), monday.plusDays(3),
                monday.plusDays(15), monday.plusDays(17),
                monday.plusDays(29), monday.plusDays(31));
    }

    @Test
    void ISO주버킷_일요일과_다음날_월요일은_다른_버킷() {
        // 일요일 S는 ISO week W의 끝, 다음날 월요일 S+1은 W+1의 시작 — 같은 "달력 주"처럼
        // 보여도 버킷이 다르다. epochDay/7(목~수 버킷)으로 계산하면 S+1이 같은 버킷으로 잘못 채택된다.
        LocalDate sunday = dayOfWeek(DayOfWeek.SUNDAY);
        assertThat(sunday.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);

        List<LocalDate> dates = RecurrenceExpander.expand(sunday, spec(2, List.of(1, 7), 4));

        assertThat(dates).containsExactly(
                sunday,                  // week W 일요일 (anchor)
                sunday.plusDays(8),      // week W+2 월요일
                sunday.plusDays(14),     // week W+2 일요일
                sunday.plusDays(22));    // week W+4 월요일
    }

    @Test
    void ISO주버킷_월요일과_그주_일요일은_같은_버킷() {
        LocalDate monday = dayOfWeek(DayOfWeek.MONDAY);
        List<LocalDate> dates = RecurrenceExpander.expand(monday, spec(2, List.of(1, 7), 4));

        assertThat(dates).containsExactly(
                monday, monday.plusDays(6),
                monday.plusDays(14), monday.plusDays(20));
    }

    @Test
    void 연도경계_연속_전개() {
        LocalDate yearEndSunday = LocalDate.of(2026, 12, 25).with(DayOfWeek.SUNDAY);
        assertThat(yearEndSunday.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);

        List<LocalDate> dates = RecurrenceExpander.expand(yearEndSunday, spec(1, List.of(1, 7), 4));

        assertThat(dates).containsExactly(
                yearEndSunday,                  // 2026-12-27 일
                yearEndSunday.plusDays(1),      // 2026-12-28 월
                yearEndSunday.plusDays(7),      // 2027-01-03 일
                yearEndSunday.plusDays(8));     // 2027-01-04 월
    }

    @Test
    void expand_수평선없음_interval4_count12() {
        LocalDate monday = dayOfWeek(DayOfWeek.MONDAY);
        LocalDate first = monday.plusDays(2); // 수요일
        List<LocalDate> dates = RecurrenceExpander.expand(first, spec(4, List.of(3), 12));

        assertThat(dates).hasSize(12);
        assertThat(dates.get(0)).isEqualTo(first);
        assertThat(dates.get(11)).isEqualTo(first.plusDays(4L * 7 * 11));
    }

    @Test
    void expand_기준일이_byDay_미매칭이면_다음_매칭부터() {
        LocalDate wednesday = dayOfWeek(DayOfWeek.WEDNESDAY);
        List<LocalDate> dates = RecurrenceExpander.expand(wednesday, spec(1, List.of(2), 2));

        assertThat(dates).containsExactly(
                wednesday.plusDays(6), wednesday.plusDays(13)); // 다음 주 화요일부터
    }

    @Test
    void expandWithin_정확히_count개_성공() {
        LocalDate monday = dayOfWeek(DayOfWeek.MONDAY);
        List<LocalDate> dates = RecurrenceExpander.expandWithin(
                monday, monday.plusDays(13), spec(1, List.of(2), 2));

        assertThat(dates).containsExactly(monday.plusDays(1), monday.plusDays(8));
    }

    @Test
    void expandWithin_to경계의_매칭은_포함() {
        LocalDate monday = dayOfWeek(DayOfWeek.MONDAY);
        List<LocalDate> dates = RecurrenceExpander.expandWithin(
                monday, monday.plusDays(8), spec(1, List.of(2), 2));

        assertThat(dates).containsExactly(monday.plusDays(1), monday.plusDays(8));
    }

    @Test
    void expandWithin_범위부족이면_예외() {
        LocalDate monday = dayOfWeek(DayOfWeek.MONDAY);
        assertThatThrownBy(() -> RecurrenceExpander.expandWithin(
                monday, monday.plusDays(6), spec(1, List.of(2), 2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expandWithin_범위밖_매칭은_세지않음() {
        LocalDate monday = dayOfWeek(DayOfWeek.MONDAY);
        // 화요일 매칭이 to 직후에 존재해도 범위 안 매칭만 센다
        assertThatThrownBy(() -> RecurrenceExpander.expandWithin(
                monday, monday, spec(1, List.of(2), 2)))
                .isInstanceOf(IllegalArgumentException.class);

        // interval=2라도 범위 밖 회차는 결과에 포함되지 않는다
        List<LocalDate> dates = RecurrenceExpander.expandWithin(
                monday, monday.plusDays(15), spec(2, List.of(2), 2));
        assertThat(dates).containsExactly(monday.plusDays(1), monday.plusDays(15));
    }

    @Test
    void Spec_of_검증_위반() {
        assertThatThrownBy(() -> Spec.of("weekly", 1, List.of(2), 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Spec.of("DAILY", 1, List.of(2), 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Spec.of(null, 1, List.of(2), 4))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Spec.of("WEEKLY", null, List.of(2), 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Spec.of("WEEKLY", 0, List.of(2), 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Spec.of("WEEKLY", 5, List.of(2), 4))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Spec.of("WEEKLY", 1, null, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Spec.of("WEEKLY", 1, List.of(), 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Spec.of("WEEKLY", 1, List.of(0), 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Spec.of("WEEKLY", 1, List.of(8), 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Spec.of("WEEKLY", 1, java.util.Arrays.asList(2, null), 4))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Spec.of("WEEKLY", 1, List.of(2), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Spec.of("WEEKLY", 1, List.of(2), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Spec.of("WEEKLY", 1, List.of(2), 13))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void Spec_of_byDays_중복제거_정렬_불변() {
        Spec spec = Spec.of("WEEKLY", 1, List.of(4, 2, 2), 4);

        assertThat(spec.byDays()).containsExactly(2, 4); // 중복 제거 + 정렬
        assertThatThrownBy(() -> spec.byDays().add(5))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
