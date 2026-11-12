package app.weave.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import app.weave.common.port.AvailabilityBlockInfo;
import app.weave.common.port.AvailabilityScopeInfo;
import app.weave.scheduling.domain.Mode;
import app.weave.scheduling.service.ScheduleRecommender.Breakdown;
import app.weave.scheduling.service.ScheduleRecommender.BusyInterval;
import app.weave.scheduling.service.ScheduleRecommender.Candidate;
import app.weave.scheduling.service.ScheduleRecommender.UserScheduleInput;
import java.time.Duration;
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

    /** busy만 있는 입력 — 선언 없음·추론 off. */
    private static UserScheduleInput in(BusyInterval... hard) {
        return new UserScheduleInput(List.of(hard), List.of(), List.of(), false);
    }

    private static UserScheduleInput in(List<BusyInterval> hard, List<BusyInterval> soft,
            List<AvailabilityScopeInfo> scopes, boolean inference) {
        return new UserScheduleInput(hard, soft, scopes, inference);
    }

    /** 추론 opt-in만 있는 입력 — 빈 캘린더면 calendarFree. */
    private static UserScheduleInput infer() {
        return new UserScheduleInput(List.of(), List.of(), List.of(), true);
    }

    private static AvailabilityScopeInfo scope(LocalDate rs, LocalDate re,
            AvailabilityBlockInfo... blocks) {
        return new AvailabilityScopeInfo(rs, re, List.of(blocks));
    }

    private static AvailabilityBlockInfo tb(LocalDateTime s, LocalDateTime e) {
        return new AvailabilityBlockInfo(null, s, e);
    }

    private static AvailabilityBlockInfo db(LocalDate d) {
        return new AvailabilityBlockInfo(d, null, null);
    }

    private static List<LocalTime[]> win(int sh, int sm, int eh, int em) {
        return List.<LocalTime[]>of(new LocalTime[]{LocalTime.of(sh, sm), LocalTime.of(eh, em)});
    }

    // ---------- 기존 슬롯 생성·정렬 (신 계약: 추론 opt-in = calendarFree) ----------

    @Test
    void 전원이_비어있고_추론을_켜면_percent_100이다() {
        Map<Long, UserScheduleInput> in = Map.of(1L, infer(), 2L, infer());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(9, 0, 12, 0), 30, 60, null, in);

        assertThat(out).isNotEmpty();
        assertThat(out.get(0).percent()).isEqualTo(100);
        assertThat(out.get(0).availableCount()).isEqualTo(2);
        assertThat(out.get(0).totalCount()).isEqualTo(2);
        assertThat(out.get(0).rank()).isEqualTo(1);
        // 추론 근거로만 가능 — direct가 아니라 calendarFree다
        assertThat(out.get(0).breakdown().calendarFree()).isEqualTo(2);
        assertThat(out.get(0).breakdown().direct()).isEqualTo(0);
    }

    @Test
    void 한_명이_HARD_겹치면_percent는_가능_비율이다() {
        // 4명 중 1명 HARD 겹침(unavailable) → 나머지 3명 추론 가능 → 3/4 = 75%
        Map<Long, UserScheduleInput> in = Map.of(
                1L, infer(), 2L, infer(), 3L, infer(),
                4L, in(busy(d1.atTime(10, 30), d1.atTime(11, 30))));
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(10, 0, 12, 0), 120, 120, null, in);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).percent()).isEqualTo(75);
        assertThat(out.get(0).availableCount()).isEqualTo(3);
        assertThat(out.get(0).totalCount()).isEqualTo(4);
        assertThat(out.get(0).breakdown().unavailable()).isEqualTo(1);
    }

    @Test
    void slotMinutes_생략_시_고정_4블록을_쓴다() {
        Map<Long, UserScheduleInput> in = Map.of(1L, infer());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                null, 30, null, null, in);

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
        // 2명, durationDays=2 — 한 명이 13일에 HARD 일정 → 12-13·13-14 구간 50%, 14-15 구간 100%
        Map<Long, UserScheduleInput> in = Map.of(
                1L, infer(),
                2L, in(List.of(busy(d2.atTime(10, 0), d2.atTime(11, 0))),
                        List.of(), List.of(), true));
        List<Candidate> out = recommender.recommend(Mode.DATE, d1, d1.plusDays(3),
                null, 30, null, 2, in);

        assertThat(out.get(0).percent()).isEqualTo(100);
        Slot.Days first = (Slot.Days) out.get(0).slot();
        assertThat(first.startDate()).isEqualTo(LocalDate.of(2026, 5, 14));
        assertThat(first.endDate()).isEqualTo(LocalDate.of(2026, 5, 15));
    }

    @Test
    void 정렬은_percent가_날짜보다_우선이다() {
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(busy(d1.atTime(9, 0), d1.atTime(10, 0))),
                        List.of(), List.of(), true),
                2L, infer());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d2,
                win(9, 0, 11, 0), 120, 120, null, in);

        assertThat(out).hasSize(2);
        assertThat(out.get(0).percent()).isEqualTo(100);
        assertThat(((Slot.Time) out.get(0).slot()).date()).isEqualTo(d2);
        assertThat(out.get(1).percent()).isEqualTo(50);
        assertThat(((Slot.Time) out.get(1).slot()).date()).isEqualTo(d1);
    }

    @Test
    void 모든_슬롯이_막히면_빈_배열이다() {
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(busy(d1.atStartOfDay(), d1.plusDays(1).atStartOfDay())),
                        List.of(), List.of(), true));
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(9, 0, 22, 0), 30, 60, null, in);

        assertThat(out).isEmpty();
    }

    @Test
    void 슬롯_끝이_자정을_넘으면_창_초과로_종료한다() {
        Map<Long, UserScheduleInput> in = Map.of(1L, infer());
        List<Candidate> out = assertTimeoutPreemptively(
                Duration.ofSeconds(10),
                () -> recommender.recommend(Mode.TIME, d1, d1,
                        win(22, 0, 23, 59), 120, 120, null, in));

        assertThat(out).isEmpty();
    }

    @Test
    void 창이_자정을_걸치지_않으면_끝까지_슬롯을_만든다() {
        Map<Long, UserScheduleInput> in = Map.of(1L, infer());
        List<Candidate> out = assertTimeoutPreemptively(
                Duration.ofSeconds(10),
                () -> recommender.recommend(Mode.TIME, d1, d1,
                        win(12, 0, 22, 0), 120, 120, null, in));

        assertThat(out).hasSize(3); // 상위 3개만 반환
    }

    @Test
    void step이_슬롯보다_짧으면_중간_후보도_탐색한다() {
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(busy(d1.atTime(9, 0), d1.atTime(10, 0))),
                        List.of(), List.of(), true));
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(9, 0, 13, 0), 30, 120, null, in);

        assertThat(out).isNotEmpty();
        Slot.Time first = (Slot.Time) out.get(0).slot();
        assertThat(first.startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(first.endTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void windows가_여러개면_각_창을_독립_탐색한다() {
        Map<Long, UserScheduleInput> in = Map.of(1L, infer());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                List.of(
                        new LocalTime[]{LocalTime.of(9, 0), LocalTime.of(11, 0)},
                        new LocalTime[]{LocalTime.of(18, 0), LocalTime.of(20, 0)}),
                60, 60, null, in);

        List<LocalTime> starts = out.stream()
                .map(c -> ((Slot.Time) c.slot()).startTime()).toList();
        assertThat(starts).contains(LocalTime.of(9, 0), LocalTime.of(18, 0));
    }

    @Test
    void 겹치는_windows는_동일_슬롯을_중복_생성하지_않는다() {
        Map<Long, UserScheduleInput> in = Map.of(1L, infer());
        List<LocalTime[]> windows = List.of(
                new LocalTime[]{LocalTime.of(9, 0), LocalTime.of(11, 0)},
                new LocalTime[]{LocalTime.of(9, 0), LocalTime.of(11, 0)});
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                windows, 60, 60, null, in);

        List<LocalTime> starts = out.stream()
                .map(c -> ((Slot.Time) c.slot()).startTime()).toList();
        assertThat(starts).doesNotHaveDuplicates();
    }

    @Test
    void stepMinutes가_슬롯보다_크거나_1미만이면_계약_위반이다() {
        Map<Long, UserScheduleInput> in = Map.of(1L, infer());
        assertThatThrownBy(() -> recommender.recommend(Mode.TIME, d1, d1,
                win(9, 0, 12, 0), 90, 60, null, in))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recommender.recommend(Mode.TIME, d1, d1,
                win(9, 0, 12, 0), 0, 60, null, in))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 참여자가_없으면_계약_위반이다() {
        assertThatThrownBy(() -> recommender.recommend(Mode.TIME, d1, d1,
                win(9, 0, 12, 0), 30, 60, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 역창_범위는_계약_위반이다() {
        Map<Long, UserScheduleInput> in = Map.of(1L, infer());
        assertThatThrownBy(() -> recommender.recommend(Mode.TIME, d2, d1,
                win(9, 0, 12, 0), 30, 60, null, in))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void DATE_모드의_durationDays_누락은_계약_위반이다() {
        Map<Long, UserScheduleInput> in = Map.of(1L, infer());
        assertThatThrownBy(() -> recommender.recommend(Mode.DATE, d1, d2,
                null, 30, null, null, in))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slotMinutes가_있으면_유효_창이_필수다() {
        Map<Long, UserScheduleInput> in = Map.of(1L, infer());
        assertThatThrownBy(() -> recommender.recommend(Mode.TIME, d1, d1,
                win(12, 0, 9, 0), 30, 60, null, in))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- 5분류 판정 (spec 판정표 + OQ-Astra 수용 조건) ----------

    @Test
    void 선언도_추론도_없으면_unknown이다() {
        // user1 근거 없음 → unknown. user2 추론 → calendarFree. 후보는 1명만 가능 집계.
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(), 2L, infer());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(10, 0, 12, 0), 120, 120, null, in);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).breakdown().unknown()).isEqualTo(1);
        assertThat(out.get(0).breakdown().calendarFree()).isEqualTo(1);
        assertThat(out.get(0).percent()).isEqualTo(50);
    }

    @Test
    void 완료_범위에_블록이_없으면_unavailable이다() {
        // COMPLETED scope [d1,d1] + 블록 0개 = "가능한 시간 없음" 응답 — unknown이 아니다
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(), List.of(), List.of(scope(d1, d1)), false),
                2L, infer());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(10, 0, 12, 0), 120, 120, null, in);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).breakdown().unavailable()).isEqualTo(1);
        assertThat(out.get(0).breakdown().calendarFree()).isEqualTo(1);
    }

    @Test
    void 완료_범위의_미선택은_추론으로_덮지_못한다() {
        // scope [d1,d1]에 10~11시만 선언, 슬롯 10~12 → 11~12 미선택 = 명시적 불가
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(), List.of(),
                        List.of(scope(d1, d1, tb(d1.atTime(10, 0), d1.atTime(11, 0)))),
                        true),
                2L, infer());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(10, 0, 12, 0), 120, 120, null, in);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).breakdown().unavailable()).isEqualTo(1);
    }

    @Test
    void 슬롯_전체를_선언으로_덮으면_direct다() {
        // 인접 블록 2개(10~11, 11~12)가 합쳐 슬롯 10~12를 완전 커버 → direct
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(), List.of(),
                        List.of(scope(d1, d1,
                                tb(d1.atTime(10, 0), d1.atTime(11, 0)),
                                tb(d1.atTime(11, 0), d1.atTime(12, 0)))),
                        false));
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(10, 0, 12, 0), 120, 120, null, in);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).breakdown().direct()).isEqualTo(1);
        assertThat(out.get(0).percent()).isEqualTo(100);
    }

    @Test
    void HARD_겹침은_수동_선언을_이긴다() {
        // 슬롯 전체 선언 + HARD 이벤트 겹침 → unavailable (선언이 HARD를 못 덮음)
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(busy(d1.atTime(10, 30), d1.atTime(11, 0))),
                        List.of(),
                        List.of(scope(d1, d1, tb(d1.atTime(10, 0), d1.atTime(12, 0)))),
                        false));
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(10, 0, 12, 0), 120, 120, null, in);

        assertThat(out).isEmpty(); // 전원 불가 — availableCount>0 필터
    }

    @Test
    void SOFT_겹침은_근거_없어도_conditional이다() {
        // 선언 없음·추론 off여도 SOFT 점유는 알려진 상태 — unknown이 아니라 conditional
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(), List.of(busy(d1.atTime(10, 0), d1.atTime(11, 0))),
                        List.of(), false),
                2L, infer());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(10, 0, 12, 0), 120, 120, null, in);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).breakdown().conditional()).isEqualTo(1);
        assertThat(out.get(0).percent()).isEqualTo(50); // conditional은 분자 제외
    }

    @Test
    void SOFT_겹침은_직접선언이_있어도_conditional이다() {
        // 개인 사본 SOFT + 같은 시간 수동 가능 선언 → conditional (direct 아님)
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(), List.of(busy(d1.atTime(10, 30), d1.atTime(11, 30))),
                        List.of(scope(d1, d1, tb(d1.atTime(10, 0), d1.atTime(12, 0)))),
                        false),
                2L, infer());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(10, 0, 12, 0), 120, 120, null, in);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).breakdown().conditional()).isEqualTo(1);
        assertThat(out.get(0).breakdown().direct()).isEqualTo(0);
    }

    @Test
    void 완료범위_미선택에_SOFT가_있어도_unavailable이다() {
        // SOFT를 옮겨도 수동 불가가 남는다 — 명시적 불가가 SOFT보다 우선
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(), List.of(busy(d1.atTime(11, 0), d1.atTime(12, 0))),
                        List.of(scope(d1, d1, tb(d1.atTime(10, 0), d1.atTime(11, 0)))),
                        false),
                2L, infer());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(10, 0, 12, 0), 120, 120, null, in);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).breakdown().unavailable()).isEqualTo(1);
        assertThat(out.get(0).breakdown().conditional()).isEqualTo(0);
    }

    @Test
    void 다른_날의_완료범위는_판정에_영향없다() {
        // scope는 d2에만 존재 — d1 슬롯은 범위 밖이므로 추론 off면 unknown
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(), List.of(), List.of(scope(d2, d2)), false),
                2L, infer());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(10, 0, 12, 0), 120, 120, null, in);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).breakdown().unknown()).isEqualTo(1);
        assertThat(out.get(0).breakdown().unavailable()).isEqualTo(0);
    }

    @Test
    void DATE_일부날짜_직접_나머지_추론은_calendarFree로_한번만_집계한다() {
        // DATE scope [d1,d1]에 d1 선언, d2는 범위 밖 + 추론 on → calendarFree 1회
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(), List.of(), List.of(scope(d1, d1, db(d1))), true));
        List<Candidate> out = recommender.recommend(Mode.DATE, d1, d2,
                null, 30, null, 2, in);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).breakdown().calendarFree()).isEqualTo(1);
        assertThat(out.get(0).percent()).isEqualTo(100);
    }

    @Test
    void DATE_범위밖_날짜가_추론없으면_unknown이다() {
        // d1만 선언·d2 근거 없음 + 추론 off → unknown
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(), List.of(), List.of(scope(d1, d1, db(d1))), false),
                2L, infer());
        List<Candidate> out = recommender.recommend(Mode.DATE, d1, d2,
                null, 30, null, 2, in);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).breakdown().unknown()).isEqualTo(1);
        assertThat(out.get(0).breakdown().calendarFree()).isEqualTo(1);
    }

    @Test
    void DATE는_같은_사용자가_모든_날짜를_충족해야_한다() {
        // user1은 d1+d2 모두 선언 → direct. user2는 scope [d1,d2]에 d1만 선언
        // → d2 미선택 = unavailable. 날짜별 가능 인원 합산으로 direct=2가 되면 안 된다.
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(), List.of(),
                        List.of(scope(d1, d2, db(d1), db(d2))), false),
                2L, in(List.of(), List.of(),
                        List.of(scope(d1, d2, db(d1))), false));
        List<Candidate> out = recommender.recommend(Mode.DATE, d1, d2,
                null, 30, null, 2, in);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).breakdown().direct()).isEqualTo(1);
        assertThat(out.get(0).breakdown().unavailable()).isEqualTo(1);
        assertThat(out.get(0).percent()).isEqualTo(50);
    }

    @Test
    void breakdown_5분류_합계는_totalCount와_같다() {
        // direct·calendarFree·conditional·unknown·unavailable 각 1명씩 — 전부 다른 상태
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(), List.of(),
                        List.of(scope(d1, d1, tb(d1.atTime(10, 0), d1.atTime(12, 0)))),
                        false),                                          // direct
                2L, infer(),                                             // calendarFree
                3L, in(List.of(), List.of(busy(d1.atTime(10, 0), d1.atTime(11, 0))),
                        List.of(), false),                               // conditional
                4L, in(),                                                // unknown
                5L, in(busy(d1.atTime(10, 0), d1.atTime(12, 0))));         // unavailable
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(10, 0, 12, 0), 120, 120, null, in);

        assertThat(out).hasSize(1);
        Breakdown b = out.get(0).breakdown();
        assertThat(b.direct() + b.calendarFree() + b.conditional()
                + b.unknown() + b.unavailable()).isEqualTo(out.get(0).totalCount());
        assertThat(b.direct() + b.calendarFree()).isEqualTo(out.get(0).availableCount());
        assertThat(out.get(0).percent()).isEqualTo(40); // floor(100*2/5)
    }

    @Test
    void HARD와_SOFT가_동시_겹치면_unavailable이다() {
        // 우선순위 1 — HARD가 있으면 SOFT 겹침과 무관하게 불가
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(busy(d1.atTime(10, 0), d1.atTime(10, 30))),
                        List.of(busy(d1.atTime(11, 0), d1.atTime(12, 0))),
                        List.of(), true),
                2L, infer());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(10, 0, 12, 0), 120, 120, null, in);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).breakdown().unavailable()).isEqualTo(1);
        assertThat(out.get(0).breakdown().conditional()).isEqualTo(0);
    }

    @Test
    void SOFT_겹침이면_범위밖_추론off여도_conditional이다() {
        // 알려진 점유(SOFT)가 있으면 근거 부족 unknown이 아니라 조정 필요.
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(), List.of(busy(d1.atTime(10, 30), d1.atTime(11, 0))),
                        List.of(), false),
                2L, infer());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(10, 0, 12, 0), 120, 120, null, in);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).breakdown().conditional()).isEqualTo(1);
        assertThat(out.get(0).breakdown().unknown()).isEqualTo(0);
    }

    @Test
    void 이벤트_종료가_슬롯_시작과_같으면_겹치지_않는다() {
        // 반개방 [start,end) — 09~10시 HARD는 10~12시 슬롯과 비겹침
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(busy(d1.atTime(9, 0), d1.atTime(10, 0))),
                        List.of(), List.of(), true));
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(10, 0, 12, 0), 120, 120, null, in);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).breakdown().calendarFree()).isEqualTo(1);
        assertThat(out.get(0).percent()).isEqualTo(100);
    }

    @Test
    void 블록_종료가_슬롯_시작과_같으면_선언으로_안_친다() {
        // 블록 09~10만 칠한 완료 범위 — 슬롯 10~12는 미선택 = unavailable
        Map<Long, UserScheduleInput> in = Map.of(
                1L, in(List.of(), List.of(),
                        List.of(scope(d1, d1, tb(d1.atTime(9, 0), d1.atTime(10, 0)))),
                        true),
                2L, infer());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(10, 0, 12, 0), 120, 120, null, in);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).breakdown().unavailable()).isEqualTo(1);
    }

    @Test
    void percent는_floor로_내림한다() {
        // 3명 중 1명만 가능 → floor(100/3) = 33
        Map<Long, UserScheduleInput> in = Map.of(
                1L, infer(), 2L, in(), 3L, in());
        List<Candidate> out = recommender.recommend(Mode.TIME, d1, d1,
                win(10, 0, 12, 0), 120, 120, null, in);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).percent()).isEqualTo(33);
    }
}
