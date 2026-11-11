package app.weave.event.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.weave.auth.jwt.JwtProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EventControllerTest {

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
    private Long otherId;
    private String token;

    @BeforeEach
    void setUp() {
        // events.proposal_id → proposals 참조라 자식부터 삭제
        jdbc.update("DELETE FROM schedule_proposal_participants");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM schedule_proposals");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES "
                + "('me@weave.local','나','나','demo'),('other@weave.local','타인','타인','demo')");
        meId = jdbc.queryForObject("SELECT id FROM users WHERE email='me@weave.local'", Long.class);
        otherId = jdbc.queryForObject("SELECT id FROM users WHERE email='other@weave.local'", Long.class);
        token = jwtProvider.createAccessToken(meId, List.of("ROLE_USER"));
    }

    @Test
    void 생성은_201과_Location과_source_manual을_반환한다() throws Exception {
        mvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"스터디\",\"startTime\":\"2026-05-20T14:00:00\",\"endTime\":\"2026-05-20T16:00:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("/api/events/")))
                .andExpect(jsonPath("$.source").value("manual"));
    }

    @Test
    void 시작이_종료보다_늦으면_400() throws Exception {
        mvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"역전\",\"startTime\":\"2026-05-20T16:00:00\",\"endTime\":\"2026-05-20T14:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVENT-INVALID-RANGE"));
    }

    @Test
    void allDay_생성은_배타_상한_규약으로_정규화된다() throws Exception {
        // 사용자 시각은 무시하고 날짜만 취한다 — 같은 날 종일도 허용
        mvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"종일\",\"startTime\":\"2026-05-12T15:30:00\",\"endTime\":\"2026-05-12T23:59:59\",\"allDay\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime").value("2026-05-12T00:00"))
                .andExpect(jsonPath("$.endTime").value("2026-05-13T00:00"));
    }

    @Test
    void allDay_다일_생성은_end_날짜가_마지막_점유일이다() throws Exception {
        // end의 날짜(5/14)가 포함 마지막 날짜 → 배타 상한은 5/15 00:00
        mvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"여행\",\"startTime\":\"2026-05-12T09:00:00\",\"endTime\":\"2026-05-14T18:00:00\",\"allDay\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime").value("2026-05-12T00:00"))
                .andExpect(jsonPath("$.endTime").value("2026-05-15T00:00"));
    }

    @Test
    void PATCH로_allDay_전환하면_기존_시각의_날짜로_정규화된다() throws Exception {
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time) VALUES "
                + "(?,'회의','2026-05-12 14:00:00','2026-05-12 16:00:00')", meId);
        Long eventId = jdbc.queryForObject(
                "SELECT id FROM events WHERE user_id=? LIMIT 1", Long.class, meId);

        mvc.perform(patch("/api/events/" + eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allDay\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("2026-05-12T00:00"))
                .andExpect(jsonPath("$.endTime").value("2026-05-13T00:00"));
    }

    @Test
    void allDay_일정에_start만_PATCH해_범위가_역전되면_400이다() throws Exception {
        // 저장된 배타 상한 [5/12, 5/13)에 start만 5/13으로 보내면 start==end → 거부.
        // 종일 일정을 이동하는 클라이언트는 start·end를 함께 보내야 한다.
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,all_day) VALUES "
                + "(?,'종일','2026-05-12 00:00:00','2026-05-13 00:00:00',true)", meId);
        Long eventId = jdbc.queryForObject(
                "SELECT id FROM events WHERE user_id=? LIMIT 1", Long.class, meId);

        mvc.perform(patch("/api/events/" + eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"2026-05-13T00:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVENT-INVALID-RANGE"));
    }

    @Test
    void allDay_일정의_start_end_동시_PATCH는_드리프트_없이_정규화된다() throws Exception {
        // 저장 [5/12, 5/15) (점유 12~14) + PATCH start=5/13·end=5/15(점유일)
        // → [5/13, 5/16) — 점유일은 13~15이며 16일을 포함하지 않는다
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,all_day) VALUES "
                + "(?,'여행','2026-05-12 00:00:00','2026-05-15 00:00:00',true)", meId);
        Long eventId = jdbc.queryForObject(
                "SELECT id FROM events WHERE user_id=? LIMIT 1", Long.class, meId);

        mvc.perform(patch("/api/events/" + eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"2026-05-13T00:00:00\",\"endTime\":\"2026-05-15T10:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("2026-05-13T00:00"))
                .andExpect(jsonPath("$.endTime").value("2026-05-16T00:00"));
    }

    @Test
    void allDay_일정에_allDay_true만_재전송해도_시각은_그대로다() throws Exception {
        // 재정규화하면 배타 상한 날짜를 점유일로 오인해 하루씩 밀린다 — 멱등이어야 한다
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,all_day) VALUES "
                + "(?,'종일','2026-05-12 00:00:00','2026-05-15 00:00:00',true)", meId);
        Long eventId = jdbc.queryForObject(
                "SELECT id FROM events WHERE user_id=? LIMIT 1", Long.class, meId);

        mvc.perform(patch("/api/events/" + eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allDay\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("2026-05-12T00:00"))
                .andExpect(jsonPath("$.endTime").value("2026-05-15T00:00"));
    }

    @Test
    void allDay_해제는_배타_상한을_마지막_점유일_235959로_환원한다() throws Exception {
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,all_day) VALUES "
                + "(?,'종일','2026-05-12 00:00:00','2026-05-15 00:00:00',true)", meId);
        Long eventId = jdbc.queryForObject(
                "SELECT id FROM events WHERE user_id=? LIMIT 1", Long.class, meId);

        mvc.perform(patch("/api/events/" + eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allDay\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("2026-05-12T00:00"))
                // 배타 상한 5/15 00:00 → 마지막 점유일 5/14 23:59:59
                .andExpect(jsonPath("$.endTime").value("2026-05-14T23:59:59"));

        // 레거시 저장값(23:59:59)을 가진 종일 일정은 환원 없이 그대로 통과한다
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,all_day) VALUES "
                + "(?,'레거시','2026-05-20 00:00:00','2026-05-20 23:59:59',true)", meId);
        Long legacyId = jdbc.queryForObject(
                "SELECT id FROM events WHERE title='레거시' LIMIT 1", Long.class);
        mvc.perform(patch("/api/events/" + legacyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allDay\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endTime").value("2026-05-20T23:59:59"));
    }

    @Test
    void allDay_정규화_결과가_역전이면_400이다() throws Exception {
        // create: end 날짜(5/11)가 start 날짜(5/12)보다 빠름 → 정규화 후 역전
        mvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"역전\",\"startTime\":\"2026-05-12T10:00:00\",\"endTime\":\"2026-05-11T10:00:00\",\"allDay\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVENT-INVALID-RANGE"));

        // update: allDay 전환 + end 날짜가 기존 start 날짜보다 빠름 → 정규화 후 역전
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time) VALUES "
                + "(?,'회의','2026-05-12 14:00:00','2026-05-12 16:00:00')", meId);
        Long eventId = jdbc.queryForObject(
                "SELECT id FROM events WHERE user_id=? LIMIT 1", Long.class, meId);
        mvc.perform(patch("/api/events/" + eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allDay\":true,\"endTime\":\"2026-05-11T10:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVENT-INVALID-RANGE"));
    }

    @Test
    void allDay_전환과_시각을_함께_PATCH하면_새_날짜로_정규화된다() throws Exception {
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time) VALUES "
                + "(?,'회의','2026-05-12 14:00:00','2026-05-12 16:00:00')", meId);
        Long eventId = jdbc.queryForObject(
                "SELECT id FROM events WHERE user_id=? LIMIT 1", Long.class, meId);

        mvc.perform(patch("/api/events/" + eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allDay\":true,\"startTime\":\"2026-05-10T08:00:00\",\"endTime\":\"2026-05-11T18:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("2026-05-10T00:00"))
                .andExpect(jsonPath("$.endTime").value("2026-05-12T00:00"));
    }

    @Test
    void allDay_다일_일정에_start만_PATCH해도_end는_드리프트하지_않는다() throws Exception {
        // 저장 [5/12, 5/15)(점유 12~14) + start만 5/13 → [5/13, 5/15)(점유 13~14).
        // 저장된 자정 end는 이미 배타 상한이라 점유일로 재해석하지 않는다 — 회귀 방지 핵심 케이스.
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,all_day) VALUES "
                + "(?,'여행','2026-05-12 00:00:00','2026-05-15 00:00:00',true)", meId);
        Long eventId = jdbc.queryForObject(
                "SELECT id FROM events WHERE user_id=? LIMIT 1", Long.class, meId);

        mvc.perform(patch("/api/events/" + eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"2026-05-13T00:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("2026-05-13T00:00"))
                .andExpect(jsonPath("$.endTime").value("2026-05-15T00:00"));
    }

    @Test
    void allDay_해제시_사용자가_보낸_end는_그대로_통과한다() throws Exception {
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,all_day) VALUES "
                + "(?,'종일','2026-05-12 00:00:00','2026-05-15 00:00:00',true)", meId);
        Long eventId = jdbc.queryForObject(
                "SELECT id FROM events WHERE user_id=? LIMIT 1", Long.class, meId);

        mvc.perform(patch("/api/events/" + eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allDay\":false,\"endTime\":\"2026-05-20T18:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("2026-05-12T00:00"))
                .andExpect(jsonPath("$.endTime").value("2026-05-20T18:00"));
    }

    @Test
    void allDay_정규화는_DB_저장값에도_반영된다() throws Exception {
        MvcResult result = mvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"종일\",\"startTime\":\"2026-05-12T15:30:00\",\"endTime\":\"2026-05-12T23:59:59\",\"allDay\":true}"))
                .andExpect(status().isCreated()).andReturn();
        Number id = com.jayway.jsonpath.JsonPath
                .read(result.getResponse().getContentAsString(), "$.id");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT start_time, end_time FROM events WHERE id=?", id.longValue());
        assertThat(row.get("start_time"))
                .isEqualTo(LocalDateTime.of(2026, 5, 12, 0, 0));
        assertThat(row.get("end_time"))
                .isEqualTo(LocalDateTime.of(2026, 5, 13, 0, 0));
    }

    @Test
    void allDay_false의_역전_범위는_기존과_같이_400이다() throws Exception {
        mvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"역전\",\"startTime\":\"2026-05-20T16:00:00\",\"endTime\":\"2026-05-20T14:00:00\",\"allDay\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVENT-INVALID-RANGE"));
    }

    @Test
    void range는_경계_걸침_일정과_end_당일을_포함한다() throws Exception {
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time) VALUES "
                + "(?,'걸침','2026-05-19 23:00:00','2026-05-20 01:00:00'),"
                + "(?,'당일','2026-05-21 09:00:00','2026-05-21 10:00:00'),"
                + "(?,'범위밖','2026-05-25 09:00:00','2026-05-25 10:00:00')",
                meId, meId, meId);

        // start=5/20, end=5/21 (포함) → 걸침(5/19 시작)과 당일(5/21) 포함, 5/25 제외
        MvcResult result = mvc.perform(get("/api/events/range")
                        .param("start", "2026-05-20").param("end", "2026-05-21")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();

        List<String> titles = com.jayway.jsonpath.JsonPath
                .read(result.getResponse().getContentAsString(), "$..title");
        assertThat(titles).containsExactlyInAnyOrder("걸침", "당일");
    }

    @Test
    void 멱등키_재요청은_같은_id를_반환한다() throws Exception {
        String body = "{\"title\":\"멱등\",\"startTime\":\"2026-05-20T14:00:00\",\"endTime\":\"2026-05-20T15:00:00\"}";
        MvcResult first = mvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "ev-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        Number firstId = com.jayway.jsonpath.JsonPath
                .read(first.getResponse().getContentAsString(), "$.id");

        MvcResult second = mvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "ev-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        Number secondId = com.jayway.jsonpath.JsonPath
                .read(second.getResponse().getContentAsString(), "$.id");

        assertThat(secondId.longValue()).isEqualTo(firstId.longValue());
    }

    @Test
    void manual_일정은_participants가_빈_배열이다() throws Exception {
        mvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"직접\",\"startTime\":\"2027-05-20T14:00:00\",\"endTime\":\"2027-05-20T15:00:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participants.length()").value(0));
    }

    @Test
    void proposal_일정은_로스터에서_본인을_뺀_참여자를_반환한다() throws Exception {
        // 타인이 주최·본인이 참여자인 확정 제안 → 내 일정의 participants는 [타인]
        jdbc.update("INSERT INTO schedule_proposals(organizer_id,title,mode,start_time,end_time,status) "
                + "VALUES (?,'약속','TIME','2027-05-20 14:00:00','2027-05-20 16:00:00','CONFIRMED')",
                otherId);
        Long pid = jdbc.queryForObject(
                "SELECT id FROM schedule_proposals ORDER BY id DESC LIMIT 1", Long.class);
        jdbc.update("INSERT INTO schedule_proposal_participants(proposal_id,user_id,response) "
                + "VALUES (?,?,'ACCEPTED')", pid, meId);
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,source,proposal_id) "
                + "VALUES (?,'약속','2027-05-20 14:00:00','2027-05-20 16:00:00','proposal',?)",
                meId, pid);
        Long eventId = jdbc.queryForObject(
                "SELECT id FROM events WHERE user_id=? LIMIT 1", Long.class, meId);

        mvc.perform(get("/api/events").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].participants.length()").value(1))
                .andExpect(jsonPath("$[0].participants[0].nickname").value("타인"));

        // PATCH 응답도 같은 계약 — participants 실데이터 유지
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/events/" + eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"약속 수정\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(1));
    }
}
