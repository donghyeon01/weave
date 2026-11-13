package app.weave.scheduling.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.weave.auth.jwt.JwtProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SchedulingControllerTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JwtProvider jwtProvider;

    private Long meId;
    private Long friendId;
    private Long strangerId;
    private String token;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM friendships");
        jdbc.update("DELETE FROM availability_blocks");
        jdbc.update("DELETE FROM availability_scopes");
        jdbc.update("DELETE FROM availability_preferences");
        jdbc.update("DELETE FROM availability_preferred_windows");
        jdbc.update("DELETE FROM users WHERE email IN ('me@weave.local','f@weave.local','s@weave.local')");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES ('me@weave.local','나','나','demo'),('f@weave.local','친구','친구','demo'),('s@weave.local','남','남','demo')");
        meId = jdbc.queryForObject("SELECT id FROM users WHERE email='me@weave.local'", Long.class);
        friendId = jdbc.queryForObject("SELECT id FROM users WHERE email='f@weave.local'", Long.class);
        strangerId = jdbc.queryForObject("SELECT id FROM users WHERE email='s@weave.local'", Long.class);
        token = jwtProvider.createAccessToken(meId, List.of("ROLE_USER"));
    }

    private void befriend(Long a, Long b) {
        jdbc.update("INSERT INTO friendships(user_id,friend_id,status) VALUES (?,?,'ACCEPTED')",
                a, b);
    }

    private void inferenceOn(Long uid) {
        jdbc.update("INSERT INTO availability_preferences(user_id,use_calendar_inference) VALUES (?,true)",
                uid);
    }

    @Test
    void 기간_역전과_DATE_duration_누락은_INVALID_INPUT이다() throws Exception {
        befriend(meId, friendId);
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-20\",\"rangeEnd\":\"2026-05-10\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));

        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DATE\",\"rangeStart\":\"2026-05-10\",\"rangeEnd\":\"2026-05-20\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));
    }

    @Test
    void 비친구_참여자는_NOT_FRIEND를_반환한다() throws Exception {
        befriend(meId, friendId);
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-10\",\"rangeEnd\":\"2026-05-12\",\"participantIds\":["
                                + strangerId + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-NOT-FRIEND"));
    }

    @Test
    void participantIds_생략_시_ACCEPTED_친구_전원으로_대체한다() throws Exception {
        befriend(meId, friendId);
        inferenceOn(meId);
        inferenceOn(friendId);
        // stranger는 친구 아님 — 대체 대상 아님
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60,\"windowStart\":\"09:00\",\"windowEnd\":\"11:00\"}"))
                .andExpect(status().isOk())
                // me+friend 2명이 전원 — stranger는 참여자가 아니다
                .andExpect(jsonPath("$.candidates[0].totalCount").value(2))
                .andExpect(jsonPath("$.candidates[0].percent").value(100))
                .andExpect(jsonPath("$.candidates[0].breakdown.calendarFree").value(2))
                // participantIds = 주최자 제외 참여자 스냅샷 + 계산 시각
                .andExpect(jsonPath("$.participantIds[0]").value(friendId))
                .andExpect(jsonPath("$.computedAt").isNotEmpty());
    }

    @Test
    void 친구_0명에서_생략은_INVALID_INPUT이다() throws Exception {
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));
    }

    @Test
    void allDay_이벤트는_해당일_전체를_busy로_처리한다() throws Exception {
        befriend(meId, friendId);
        inferenceOn(meId);
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,all_day,source) VALUES (?,'종일','2026-05-12 00:00:00','2026-05-12 23:59:00',true,'manual')",
                friendId);
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60}"))
                .andExpect(status().isOk())
                // 친구는 종일 HARD로 unavailable, 나는 추론으로 calendarFree → 50%
                .andExpect(jsonPath("$.candidates").isNotEmpty())
                .andExpect(jsonPath("$.candidates[*].percent").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(50))))
                .andExpect(jsonPath("$.candidates[0].breakdown.unavailable").value(1));
    }

    @Test
    void allDay_다일_이벤트는_모든_점유일을_busy로_처리한다() throws Exception {
        // DATE 제안 확정 저장 규약 [12일 00:00, 15일 00:00) — 12~14일 전부 busy여야 한다.
        // 기존엔 첫날(12일)만 막혀 13~16일 구간이 100%로 추천되는 버그가 있었다.
        befriend(meId, friendId);
        inferenceOn(meId);
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,all_day,source) VALUES (?,'종일','2026-05-12 00:00:00','2026-05-15 00:00:00',true,'manual')",
                friendId);
        // durationDays=4 → 후보는 12~15, 13~16 둘뿐이며 둘 다 busy와 겹침
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DATE\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-16\",\"durationDays\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isNotEmpty())
                .andExpect(jsonPath("$.candidates[*].percent").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(50))));
    }

    @Test
    void windows와_stepMinutes를_받아들인다() throws Exception {
        befriend(meId, friendId);
        inferenceOn(meId);
        inferenceOn(friendId);
        // step30·slot60 → 창1 후보 09:00/09:30/10:00, 창2 후보 18:00/18:30/19:00
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60,\"stepMinutes\":30,\"windows\":[{\"startTime\":\"09:00\",\"endTime\":\"11:00\"},{\"startTime\":\"18:00\",\"endTime\":\"20:00\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isNotEmpty())
                // 반환 슬롯은 요청한 두 창 안에만 존재해야 한다
                .andExpect(jsonPath("$.candidates[*].slot.startTime").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(
                                org.hamcrest.Matchers.oneOf("09:00", "09:30", "10:00",
                                        "18:00", "18:30", "19:00")))))
                .andExpect(jsonPath("$.candidates[*].slot.endTime").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(
                                org.hamcrest.Matchers.oneOf("10:00", "10:30", "11:00",
                                        "19:00", "19:30", "20:00")))));
    }

    @Test
    void stepMinutes_생략_시_기본_간격_30으로_탐색한다() throws Exception {
        befriend(meId, friendId);
        inferenceOn(meId);
        inferenceOn(friendId);
        // 창 09:00~10:30·slot60 — step30이면 09:00과 09:30 슬롯 둘 다 후보가 된다
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60,\"windowStart\":\"09:00\",\"windowEnd\":\"10:30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[*].slot.startTime").value(
                        org.hamcrest.Matchers.contains("09:00", "09:30")));
    }

    @Test
    void windows는_windowStart_End보다_우선한다() throws Exception {
        befriend(meId, friendId);
        inferenceOn(meId);
        inferenceOn(friendId);
        // windowStart/End(09~11시)와 모순되는 windows(18~20시)를 함께 전송 → windows 적용
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60,\"stepMinutes\":60,\"windowStart\":\"09:00\",\"windowEnd\":\"11:00\",\"windows\":[{\"startTime\":\"18:00\",\"endTime\":\"20:00\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[*].slot.startTime").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(
                                org.hamcrest.Matchers.oneOf("18:00", "19:00")))));
    }

    @Test
    void 빈_windows와_null_원소는_INVALID_INPUT이다() throws Exception {
        befriend(meId, friendId);
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60,\"windows\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));

        // windows:[null] — 컨트롤러 매핑이 NPE 없이 서비스 검증으로 넘겨야 한다
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60,\"windows\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));
    }

    @Test
    void 창_역전과_step_초과는_INVALID_INPUT이다() throws Exception {
        befriend(meId, friendId);
        // windows 원소의 start>=end → 400
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60,\"windows\":[{\"startTime\":\"11:00\",\"endTime\":\"09:00\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));

        // stepMinutes > slotMinutes → 400
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60,\"stepMinutes\":90}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));
    }

    // ---------- Task 6: 가용성 합성 5분류 (신 계약 — percent 의미 변경) ----------

    @Test
    void 가용성_데이터가_없으면_unknown이라_후보가_없다() throws Exception {
        // 신 계약: 이벤트가 없어도 추론 opt-in·선언이 없으면 unknown — 가능으로 집계 금지.
        // 구 계약의 "이벤트 없음 = 가능" 추론은 폐기됐다.
        befriend(meId, friendId);
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isEmpty());
    }

    @Test
    void SOFT_이벤트는_conditional로_집계된다() throws Exception {
        befriend(meId, friendId);
        inferenceOn(meId);
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,all_day,source,busy_policy) VALUES (?,'운동','2026-05-12 10:00:00','2026-05-12 11:00:00',false,'manual','SOFT')",
                friendId);
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60,\"windowStart\":\"10:00\",\"windowEnd\":\"11:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isNotEmpty())
                // 친구 SOFT 겹침 = conditional — 분자 제외·분모 포함 → 50%
                .andExpect(jsonPath("$.candidates[*].percent").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(50))))
                .andExpect(jsonPath("$.candidates[0].breakdown.conditional").value(1))
                .andExpect(jsonPath("$.candidates[0].breakdown.calendarFree").value(1));
    }

    @Test
    void FREE_이벤트는_busy로_해석하지_않는다() throws Exception {
        // FREE는 점유가 아니므로 unavailable·conditional 어디에도 넣지 않는다
        befriend(meId, friendId);
        inferenceOn(meId);
        inferenceOn(friendId);
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,all_day,source,busy_policy) VALUES (?,'기념일','2026-05-12 10:00:00','2026-05-12 11:00:00',false,'manual','FREE')",
                friendId);
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60,\"windowStart\":\"09:00\",\"windowEnd\":\"11:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[*].percent").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(100))))
                .andExpect(jsonPath("$.candidates[0].breakdown.calendarFree").value(2));
    }

    @Test
    void 완료_범위의_미선택은_unavailable로_분류된다() throws Exception {
        // 친구가 COMPLETED TIME scope [12,12]에 09~10시만 칠함 → 10~11시 슬롯 미선택 = 명시적 불가.
        // 추론을 켜도 덮지 않는다 (spec 보정 — unknown이 아니다).
        befriend(meId, friendId);
        inferenceOn(meId);
        inferenceOn(friendId);
        jdbc.update("INSERT INTO availability_scopes(user_id,mode,range_start,range_end,status,revision) VALUES (?,'TIME','2026-05-12','2026-05-12','COMPLETED',1)",
                friendId);
        Long scopeId = jdbc.queryForObject(
                "SELECT id FROM availability_scopes WHERE user_id=?", Long.class, friendId);
        jdbc.update("INSERT INTO availability_blocks(scope_id,kind,start_at,end_at) VALUES (?,'TIME','2026-05-12 09:00:00','2026-05-12 10:00:00')",
                scopeId);
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60,\"windowStart\":\"10:00\",\"windowEnd\":\"11:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isNotEmpty())
                .andExpect(jsonPath("$.candidates[0].breakdown.unavailable").value(1));
    }

    @Test
    void 슬롯_전체를_칠한_사용자는_direct로_집계된다() throws Exception {
        befriend(meId, friendId);
        inferenceOn(meId);
        jdbc.update("INSERT INTO availability_scopes(user_id,mode,range_start,range_end,status,revision) VALUES (?,'TIME','2026-05-12','2026-05-12','COMPLETED',1)",
                friendId);
        Long scopeId = jdbc.queryForObject(
                "SELECT id FROM availability_scopes WHERE user_id=?", Long.class, friendId);
        jdbc.update("INSERT INTO availability_blocks(scope_id,kind,start_at,end_at) VALUES (?,'TIME','2026-05-12 10:00:00','2026-05-12 11:00:00')",
                scopeId);
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60,\"windowStart\":\"10:00\",\"windowEnd\":\"11:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].breakdown.direct").value(1))
                .andExpect(jsonPath("$.candidates[0].percent").value(100));
    }

    @Test
    void TIME_선언만_있으면_DATE_후보는_unknown이다() throws Exception {
        // 모드 스코핑 — TIME 블록은 하루 전체를 덮어도 DATE 판정 근거가 아니다 (OQ2)
        befriend(meId, friendId);
        inferenceOn(meId);
        jdbc.update("INSERT INTO availability_scopes(user_id,mode,range_start,range_end,status,revision) VALUES (?,'TIME','2026-05-12','2026-05-12','COMPLETED',1)",
                friendId);
        Long scopeId = jdbc.queryForObject(
                "SELECT id FROM availability_scopes WHERE user_id=?", Long.class, friendId);
        jdbc.update("INSERT INTO availability_blocks(scope_id,kind,start_at,end_at) VALUES (?,'TIME','2026-05-12 00:00:00','2026-05-13 00:00:00')",
                scopeId);
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DATE\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"durationDays\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isNotEmpty())
                .andExpect(jsonPath("$.candidates[0].breakdown.unknown").value(1))
                .andExpect(jsonPath("$.candidates[0].breakdown.calendarFree").value(1));
    }

    @Test
    void DATE_선언만_있으면_TIME_후보는_unknown이다() throws Exception {
        // 역방향 모드 스코핑 — DATE 블록은 TIME 후보 판정에 쓰이지 않는다 (OQ2)
        befriend(meId, friendId);
        inferenceOn(meId);
        jdbc.update("INSERT INTO availability_scopes(user_id,mode,range_start,range_end,status,revision) VALUES (?,'DATE','2026-05-12','2026-05-12','COMPLETED',1)",
                friendId);
        Long scopeId = jdbc.queryForObject(
                "SELECT id FROM availability_scopes WHERE user_id=?", Long.class, friendId);
        jdbc.update("INSERT INTO availability_blocks(scope_id,kind,block_date) VALUES (?,'DATE','2026-05-12')",
                scopeId);
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60,\"windowStart\":\"10:00\",\"windowEnd\":\"11:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isNotEmpty())
                .andExpect(jsonPath("$.candidates[0].breakdown.unknown").value(1))
                .andExpect(jsonPath("$.candidates[0].breakdown.direct").value(0));
    }

    @Test
    void DATE_선언_공존시_각_모드에서만_판정한다() throws Exception {
        // DATE scope [12,13] 양일 선언 + TIME scope 없음 — DATE 추천에서만 direct
        befriend(meId, friendId);
        inferenceOn(meId);
        jdbc.update("INSERT INTO availability_scopes(user_id,mode,range_start,range_end,status,revision) VALUES (?,'DATE','2026-05-12','2026-05-13','COMPLETED',1)",
                friendId);
        Long scopeId = jdbc.queryForObject(
                "SELECT id FROM availability_scopes WHERE user_id=?", Long.class, friendId);
        jdbc.update("INSERT INTO availability_blocks(scope_id,kind,block_date) VALUES (?,'DATE','2026-05-12'),(?,'DATE','2026-05-13')",
                scopeId, scopeId);
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DATE\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-13\",\"durationDays\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].breakdown.direct").value(1))
                .andExpect(jsonPath("$.candidates[0].percent").value(100));
    }

    // ---------- Task 7: recurrence 추천 ----------

    @Test
    void recurrence_요청은_시리즈_후보를_반환한다() throws Exception {
        befriend(meId, friendId);
        inferenceOn(meId);
        inferenceOn(friendId);
        // 화요일(2) 매주 — [05-12, 06-02]에 화요일 정확히 4회
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-06-02\",\"slotMinutes\":60,\"windowStart\":\"10:00\",\"windowEnd\":\"11:00\",\"recurrence\":{\"freq\":\"WEEKLY\",\"interval\":1,\"byDay\":[2],\"count\":4}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].occurrenceCount").value(4))
                .andExpect(jsonPath("$.candidates[0].occurrences.length()").value(4))
                .andExpect(jsonPath("$.candidates[0].occurrences[0].date")
                        .value("2026-05-12"))
                .andExpect(jsonPath("$.candidates[0].occurrences[0].percent")
                        .value(100))
                .andExpect(jsonPath("$.candidates[0].minOccurrencePercent")
                        .value(100))
                .andExpect(jsonPath("$.candidates[0].percent").value(100));
    }

    @Test
    void DATE_모드에서_recurrence는_INVALID_INPUT이다() throws Exception {
        befriend(meId, friendId);
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DATE\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-06-02\",\"durationDays\":1,\"recurrence\":{\"freq\":\"WEEKLY\",\"interval\":1,\"byDay\":[2],\"count\":4}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));
    }

    @Test
    void 범위에_count만큼_회차가_없으면_INVALID_INPUT이다() throws Exception {
        befriend(meId, friendId);
        // 같은 범위에 화요일은 4회뿐 — count 8은 못 채운다
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-06-02\",\"recurrence\":{\"freq\":\"WEEKLY\",\"interval\":1,\"byDay\":[2],\"count\":8}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));
    }

    @Test
    void 잘못된_recurrence_스펙은_INVALID_INPUT이다() throws Exception {
        befriend(meId, friendId);
        String[] bodies = {
                "{\"freq\":\"WEEKLY\",\"interval\":1,\"byDay\":[2],\"count\":1}",
                "{\"freq\":\"WEEKLY\",\"interval\":5,\"byDay\":[2],\"count\":4}",
                "{\"freq\":\"WEEKLY\",\"interval\":1,\"byDay\":[],\"count\":4}",
                "{\"freq\":\"DAILY\",\"interval\":1,\"byDay\":[2],\"count\":4}"};
        for (String recurrence : bodies) {
            mvc.perform(post("/api/scheduling")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-06-02\",\"recurrence\":"
                                    + recurrence + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));
        }
    }

    @Test
    void 선호_윈도우_등록자가_있으면_preferenceScore가_계산된다() throws Exception {
        befriend(meId, friendId);
        inferenceOn(meId);
        inferenceOn(friendId);
        // 친구가 화요일 10~11시를 선호 등록 — 05-12(화) 10~11시 슬롯에 적합도 100%
        jdbc.update("INSERT INTO availability_preferred_windows(user_id,day_of_week,start_time,end_time) VALUES (?,2,'10:00:00','11:00:00')",
                friendId);
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60,\"windowStart\":\"10:00\",\"windowEnd\":\"11:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].preferenceScore").value(100))
                .andExpect(jsonPath("$.candidates[0].preferenceRespondentCount")
                        .value(1));
    }

    @Test
    void 단일_추천의_시리즈_필드는_null이다() throws Exception {
        // recurrence 없는 단일 추천 회귀 — 신규 필드는 존재하되 null이어야 한다
        befriend(meId, friendId);
        inferenceOn(meId);
        inferenceOn(friendId);
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60,\"windowStart\":\"10:00\",\"windowEnd\":\"11:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].occurrenceCount")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.candidates[0].minOccurrencePercent")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.candidates[0].occurrences")
                        .value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void 입력_상한_초과는_INVALID_INPUT이고_경계값은_통과한다() throws Exception {
        befriend(meId, friendId);
        // 기간 367일 — 상한 366일 초과
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-01-01\",\"rangeEnd\":\"2027-01-02\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));
        // 기간 정확히 366일 — 통과
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-01-01\",\"rangeEnd\":\"2027-01-01\"}"))
                .andExpect(status().isOk());
        // DATE durationDays 31 — 상한 30 초과 / 30은 통과
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DATE\",\"rangeStart\":\"2026-05-10\",\"rangeEnd\":\"2026-06-15\",\"durationDays\":31}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DATE\",\"rangeStart\":\"2026-05-10\",\"rangeEnd\":\"2026-06-15\",\"durationDays\":30}"))
                .andExpect(status().isOk());
        // windows 9개 — 상한 8 초과
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"windows\":["
                                + "{\"startTime\":\"09:00\",\"endTime\":\"10:00\"},{\"startTime\":\"10:00\",\"endTime\":\"11:00\"},"
                                + "{\"startTime\":\"11:00\",\"endTime\":\"12:00\"},{\"startTime\":\"12:00\",\"endTime\":\"13:00\"},"
                                + "{\"startTime\":\"13:00\",\"endTime\":\"14:00\"},{\"startTime\":\"14:00\",\"endTime\":\"15:00\"},"
                                + "{\"startTime\":\"15:00\",\"endTime\":\"16:00\"},{\"startTime\":\"16:00\",\"endTime\":\"17:00\"},"
                                + "{\"startTime\":\"17:00\",\"endTime\":\"18:00\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));
        // stepMinutes 4 — 하한 5 미만 / slotMinutes 1441 — 상한 1440 초과
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"stepMinutes\":4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":1441}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));
    }

    @Test
    void 참여자_상한은_명시_생략_모두에_적용된다() throws Exception {
        befriend(meId, friendId);
        // 명시 51명 — 인원 상한은 NOT_FRIEND보다 먼저 적용되는 형식 검증
        String ids51 = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(i -> "90" + i)
                .collect(java.util.stream.Collectors.joining(","));
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"participantIds\":["
                                + ids51 + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));
        // 친구 정확히 50명(friendId + p1~p49) — 생략(전체)·명시 50명 모두 상한 이내라 통과
        List<Long> friendIds = new java.util.ArrayList<>();
        friendIds.add(friendId);
        for (int i = 1; i <= 49; i++) {
            jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES (?,?,?,'demo')",
                    "p" + i + "@weave.local", "p" + i, "p" + i);
            Long pid = jdbc.queryForObject("SELECT id FROM users WHERE email=?",
                    Long.class, "p" + i + "@weave.local");
            befriend(meId, pid);
            friendIds.add(pid);
        }
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\"}"))
                .andExpect(status().isOk());
        // 51번째 친구 추가 — 생략은 상한 초과 400, 명시 50명 부분집합은 통과
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES ('p50@weave.local','p50','p50','demo')");
        befriend(meId, jdbc.queryForObject("SELECT id FROM users WHERE email='p50@weave.local'",
                Long.class));
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));
        String explicit50 = friendIds.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"participantIds\":["
                                + explicit50 + "]}"))
                .andExpect(status().isOk());
    }
}
