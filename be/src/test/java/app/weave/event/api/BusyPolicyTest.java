package app.weave.event.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.weave.auth.jwt.JwtProvider;
import app.weave.common.port.BusyBlock;
import app.weave.event.service.EventAccessAdapter;
import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** events.busy_policy — 생성·수정·조회 계약과 busy 블록 필터링 (spec v2-availability). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BusyPolicyTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JwtProvider jwtProvider;
    @Autowired
    EventAccessAdapter eventAccess;

    private Long meId;
    private Long p1Id;
    private Long p2Id;
    private String meToken;
    private String p1Token;
    private String p2Token;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM chat_messages");
        jdbc.update("DELETE FROM chat_room_members");
        jdbc.update("DELETE FROM chat_rooms");
        jdbc.update("DELETE FROM schedule_proposal_participants");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM schedule_proposals");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM friendships");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES "
                + "('me@weave.local','나','나','demo'),('p1@weave.local','일','일','demo'),"
                + "('p2@weave.local','이','이','demo')");
        meId = idOf("me@weave.local");
        p1Id = idOf("p1@weave.local");
        p2Id = idOf("p2@weave.local");
        meToken = jwtProvider.createAccessToken(meId, List.of("ROLE_USER"));
        p1Token = jwtProvider.createAccessToken(p1Id, List.of("ROLE_USER"));
        p2Token = jwtProvider.createAccessToken(p2Id, List.of("ROLE_USER"));
    }

    private Long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    }

    private String eventBody(String title, String busyPolicy) {
        return "{\"title\":\"" + title + "\","
                + "\"startTime\":\"2027-06-01T10:00:00\",\"endTime\":\"2027-06-01T11:00:00\""
                + (busyPolicy == null ? "" : ",\"busyPolicy\":\"" + busyPolicy + "\"")
                + "}";
    }

    private Long createEvent(String title, String busyPolicy) throws Exception {
        MvcResult r = mvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody(title, busyPolicy)))
                .andExpect(status().isCreated()).andReturn();
        return com.jayway.jsonpath.JsonPath.<Number>read(
                r.getResponse().getContentAsString(), "$.id").longValue();
    }

    @Test
    void 생성시_busyPolicy를_생략하면_HARD다() throws Exception {
        mvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody("기본", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.busyPolicy").value("HARD"));
    }

    @Test
    void SOFT와_FREE로_생성하고_조회와_수정응답에_반영된다() throws Exception {
        Long softId = createEvent("소프트", "SOFT");
        Long freeId = createEvent("프리", "FREE");

        MvcResult list = mvc.perform(get("/api/events")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk()).andReturn();
        String body = list.getResponse().getContentAsString();
        assertThat(com.jayway.jsonpath.JsonPath.<List<String>>read(
                body, "$[?(@.id == " + softId + ")].busyPolicy"))
                .containsExactly("SOFT");
        assertThat(com.jayway.jsonpath.JsonPath.<List<String>>read(
                body, "$[?(@.id == " + freeId + ")].busyPolicy"))
                .containsExactly("FREE");

        // PATCH 생략 시 기존 값 유지
        mvc.perform(patch("/api/events/" + softId)
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"소프트2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busyPolicy").value("SOFT"));

        // 명시 변경 허용
        mvc.perform(patch("/api/events/" + softId)
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"busyPolicy\":\"FREE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busyPolicy").value("FREE"));
    }

    @Test
    void 잘못된_busyPolicy는_400이다() throws Exception {
        mvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody("불량", "BOGUS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-VALIDATION"));

        Long id = createEvent("정상", null);
        mvc.perform(patch("/api/events/" + id)
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"busyPolicy\":\"medium\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-VALIDATION"));
    }

    @Test
    void busyBlocksOf는_FREE를_제외하고_HARD와_SOFT를_포함한다() throws Exception {
        createEvent("하드", "HARD");
        createEvent("소프트", "SOFT");
        createEvent("프리", "FREE");

        List<BusyBlock> blocks = eventAccess.busyBlocksOf(meId,
                LocalDateTime.of(2027, 6, 1, 0, 0), LocalDateTime.of(2027, 6, 2, 0, 0));

        assertThat(blocks).hasSize(2);
    }

    @Test
    void 개인_일정을_SOFT로_바꿔도_본인_busyBlocksOf에_블록으로_남는다() throws Exception {
        // SOFT는 시간 점유 — 추천의 conditional 판정은 T6이 정책을 읽어 처리한다
        Long id = createEvent("조정가능", "SOFT");
        mvc.perform(patch("/api/events/" + id)
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"busyPolicy\":\"SOFT\"}"))
                .andExpect(status().isOk());

        assertThat(eventAccess.busyBlocksOf(meId,
                LocalDateTime.of(2027, 6, 1, 0, 0), LocalDateTime.of(2027, 6, 2, 0, 0)))
                .hasSize(1);
    }

    @Test
    void 제안_확정으로_생성된_이벤트는_HARD다() throws Exception {
        for (Long fid : List.of(p1Id, p2Id)) {
            jdbc.update("INSERT INTO friendships(user_id,friend_id,status) "
                    + "VALUES (?,?,'ACCEPTED')", meId, fid);
        }
        MvcResult r = mvc.perform(post("/api/scheduling/proposals")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"확정 회의\",\"slot\":{\"type\":\"TIME\","
                                + "\"date\":\"2027-06-05\",\"startTime\":\"14:00\","
                                + "\"endTime\":\"16:00\"},\"participantIds\":["
                                + p1Id + "," + p2Id + "]}"))
                .andExpect(status().isCreated()).andReturn();
        Long pid = com.jayway.jsonpath.JsonPath.<Number>read(
                r.getResponse().getContentAsString(), "$.id").longValue();
        for (String token : List.of(p1Token, p2Token)) {
            mvc.perform(post("/api/scheduling/proposals/" + pid + "/respond")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"response\":\"ACCEPTED\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/scheduling/proposals/" + pid + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal' AND busy_policy='HARD'",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal' AND busy_policy<>'HARD'",
                Integer.class)).isEqualTo(0);
    }

    @Test
    void 친구_미리보기는_HARD와_SOFT를_점유블록으로_FREE와_정책필드는_노출하지_않는다() throws Exception {
        jdbc.update("INSERT INTO friendships(user_id,friend_id,status) "
                + "VALUES (?,?,'ACCEPTED')", meId, p1Id);
        Long fid = jdbc.queryForObject(
                "SELECT id FROM friendships WHERE user_id=? AND friend_id=?", Long.class,
                meId, p1Id);
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,busy_policy) "
                + "VALUES (?,'하드','2027-05-20 10:00:00','2027-05-20 11:00:00','HARD'),"
                + "(?,'소프트','2027-05-21 10:00:00','2027-05-21 11:00:00','SOFT'),"
                + "(?,'프리','2027-05-22 10:00:00','2027-05-22 11:00:00','FREE')",
                p1Id, p1Id, p1Id);

        mvc.perform(get("/api/friends/" + fid + "/schedule")
                        .param("start", "2027-05-01").param("end", "2027-05-31")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks.length()").value(2))
                .andExpect(jsonPath("$.blocks[0].busyPolicy").doesNotExist());
    }
}
