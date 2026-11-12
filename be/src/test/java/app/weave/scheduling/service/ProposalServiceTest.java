package app.weave.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.weave.auth.jwt.JwtProvider;
import app.weave.notification.service.NotificationPayload;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProposalServiceTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JwtProvider jwtProvider;

    @MockitoSpyBean
    app.weave.notification.service.NotificationPublisher publisher;
    @MockitoSpyBean
    app.weave.chat.service.ChatService chatService;

    private Long meId;
    private Long p1Id;
    private Long p2Id;
    private Long p3Id;
    private Long outsiderId;
    private String meToken;
    private String p1Token;
    private String p2Token;
    private String outsiderToken;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM chat_messages");
        jdbc.update("DELETE FROM chat_room_members");
        jdbc.update("DELETE FROM chat_rooms");
        jdbc.update("DELETE FROM schedule_proposal_participants");
        jdbc.update("DELETE FROM schedule_proposals");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM friendships");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES "
                + "('me@weave.local','나','나','demo'),('p1@weave.local','일','일','demo'),"
                + "('p2@weave.local','이','이','demo'),('p3@weave.local','삼','삼','demo'),"
                + "('out@weave.local','외','외','demo')");
        meId = idOf("me@weave.local");
        p1Id = idOf("p1@weave.local");
        p2Id = idOf("p2@weave.local");
        p3Id = idOf("p3@weave.local");
        outsiderId = idOf("out@weave.local");
        meToken = jwtProvider.createAccessToken(meId, List.of("ROLE_USER"));
        p1Token = jwtProvider.createAccessToken(p1Id, List.of("ROLE_USER"));
        p2Token = jwtProvider.createAccessToken(p2Id, List.of("ROLE_USER"));
        outsiderToken = jwtProvider.createAccessToken(outsiderId, List.of("ROLE_USER"));
        befriend(meId, p1Id);
        befriend(meId, p2Id);
        befriend(meId, p3Id);
    }

    private Long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    }

    private void befriend(Long a, Long b) {
        jdbc.update("INSERT INTO friendships(user_id,friend_id,status) VALUES (?,?,'ACCEPTED')",
                a, b);
    }

    private Long createProposal() throws Exception {
        return createProposal(List.of(p1Id, p2Id));
    }

    private Long createProposal(List<Long> participantIds) throws Exception {
        String ids = participantIds.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        MvcResult r = mvc.perform(post("/api/scheduling/proposals")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"캡스톤 회의\",\"slot\":{\"type\":\"TIME\",\"date\":\"2026-05-12\",\"startTime\":\"14:00\",\"endTime\":\"16:00\"},\"participantIds\":["
                                + ids + "]}"))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Location",
                                org.hamcrest.Matchers.startsWith("/api/scheduling/proposals/")))
                .andReturn();
        return com.jayway.jsonpath.JsonPath.<Number>read(
                r.getResponse().getContentAsString(), "$.id").longValue();
    }

    /** 정례 제안 생성 — 2026-05-12는 화요일(ISO 2). */
    private MvcResult createRecurring(String recurrenceJson) throws Exception {
        return mvc.perform(post("/api/scheduling/proposals")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"정례 회의\",\"slot\":{\"type\":\"TIME\","
                                + "\"date\":\"2026-05-12\",\"startTime\":\"14:00\","
                                + "\"endTime\":\"16:00\"},\"participantIds\":["
                                + p1Id + "," + p2Id + "],\"recurrence\":"
                                + recurrenceJson + "}"))
                .andReturn();
    }

    @Test
    void 정례_제안_생성은_recurrence컬럼과_count개_회차행을_물질화한다() throws Exception {
        MvcResult r = createRecurring(
                "{\"freq\":\"WEEKLY\",\"interval\":1,\"byDay\":[2],\"count\":4}");
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        Long id = com.jayway.jsonpath.JsonPath.<Number>read(
                r.getResponse().getContentAsString(), "$.id").longValue();

        // proposal recurrence 컬럼 — 첫 회차 범위는 기존 start_time/end_time에 유지
        assertThat(jdbc.queryForObject(
                "SELECT recurrence_freq FROM schedule_proposals WHERE id=?",
                String.class, id)).isEqualTo("WEEKLY");
        assertThat(jdbc.queryForObject(
                "SELECT recurrence_interval FROM schedule_proposals WHERE id=?",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT recurrence_by_days FROM schedule_proposals WHERE id=?",
                String.class, id)).isEqualTo("2");
        assertThat(jdbc.queryForObject(
                "SELECT occurrence_count FROM schedule_proposals WHERE id=?",
                Integer.class, id)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT start_time FROM schedule_proposals WHERE id=?", String.class, id))
                .startsWith("2026-05-12 14:00");

        // 회차 물질화 — seq 0..3, 매주 화요일 같은 시각
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM schedule_proposal_occurrences WHERE proposal_id=?",
                Integer.class, id)).isEqualTo(4);
        List<String> starts = jdbc.queryForList(
                "SELECT start_time FROM schedule_proposal_occurrences "
                        + "WHERE proposal_id=? ORDER BY seq", String.class, id);
        assertThat(starts).hasSize(4);
        assertThat(starts.get(0)).startsWith("2026-05-12 14:00");
        assertThat(starts.get(1)).startsWith("2026-05-19 14:00");
        assertThat(starts.get(2)).startsWith("2026-05-26 14:00");
        assertThat(starts.get(3)).startsWith("2026-06-02 14:00");

        // 응답 — recurrence·occurrenceCount·occurrences (계약 §3.7)
        assertThat(com.jayway.jsonpath.JsonPath.<Integer>read(
                r.getResponse().getContentAsString(), "$.occurrenceCount")).isEqualTo(4);
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(
                r.getResponse().getContentAsString(), "$.recurrence.freq"))
                .isEqualTo("WEEKLY");
        assertThat(com.jayway.jsonpath.JsonPath.<List<Integer>>read(
                r.getResponse().getContentAsString(), "$.recurrence.byDay"))
                .isEqualTo(List.of(2));
        assertThat(com.jayway.jsonpath.JsonPath.<List<?>>read(
                r.getResponse().getContentAsString(), "$.occurrences")).hasSize(4);
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(
                r.getResponse().getContentAsString(), "$.occurrences[0].date"))
                .isEqualTo("2026-05-12");
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(
                r.getResponse().getContentAsString(), "$.occurrences[0].startTime"))
                .isEqualTo("14:00");
    }

    @Test
    void slot날짜_요일이_byDay에_없으면_400이다() throws Exception {
        // 2026-05-12(화)인데 byDay=[4](목)
        MvcResult r = createRecurring(
                "{\"freq\":\"WEEKLY\",\"interval\":1,\"byDay\":[4],\"count\":4}");
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(
                r.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("SCHEDULING-INVALID-INPUT");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM schedule_proposals", Integer.class)).isEqualTo(0);
    }

    @Test
    void DATE슬롯에_recurrence를_지정하면_400이다() throws Exception {
        mvc.perform(post("/api/scheduling/proposals")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"주말 여행\",\"slot\":{\"type\":\"DATE\","
                                + "\"startDate\":\"2026-05-12\",\"endDate\":\"2026-05-14\"},"
                                + "\"participantIds\":[" + p1Id + "],"
                                + "\"recurrence\":{\"freq\":\"WEEKLY\",\"interval\":1,"
                                + "\"byDay\":[2],\"count\":4}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING-INVALID-INPUT"));
    }

    @Test
    void recurrence_형식위반은_400이다() throws Exception {
        MvcResult r = createRecurring(
                "{\"freq\":\"DAILY\",\"interval\":1,\"byDay\":[2],\"count\":4}");
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(
                r.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("SCHEDULING-INVALID-INPUT");
    }

    @Test
    void 단일_제안도_occurrence_1행이_물질화되고_응답은_occurrenceCount1이다() throws Exception {
        Long id = createProposal();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM schedule_proposal_occurrences WHERE proposal_id=?",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT seq FROM schedule_proposal_occurrences WHERE proposal_id=?",
                Integer.class, id)).isEqualTo(0);
        assertThat(jdbc.queryForObject(
                "SELECT start_time FROM schedule_proposal_occurrences WHERE proposal_id=?",
                String.class, id)).startsWith("2026-05-12 14:00");

        MvcResult r = mvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/api/scheduling/proposals/" + id)
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occurrenceCount").value(1))
                .andExpect(jsonPath("$.recurrence")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andReturn();
        assertThat(com.jayway.jsonpath.JsonPath.<Object>read(
                r.getResponse().getContentAsString(), "$.occurrences")).isNull();
    }

    @Test
    void 정례_제안의_멱등_재생성은_회차가_중복되지_않는다() throws Exception {
        String body = "{\"title\":\"정례 회의\",\"slot\":{\"type\":\"TIME\","
                + "\"date\":\"2026-05-12\",\"startTime\":\"14:00\",\"endTime\":\"16:00\"},"
                + "\"participantIds\":[" + p1Id + "," + p2Id + "],"
                + "\"recurrence\":{\"freq\":\"WEEKLY\",\"interval\":1,\"byDay\":[2],"
                + "\"count\":4}}";

        MvcResult r1 = mvc.perform(post("/api/scheduling/proposals")
                        .header("Authorization", "Bearer " + meToken)
                        .header("Idempotency-Key", "rec-key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        Long id1 = com.jayway.jsonpath.JsonPath.<Number>read(
                r1.getResponse().getContentAsString(), "$.id").longValue();

        MvcResult r2 = mvc.perform(post("/api/scheduling/proposals")
                        .header("Authorization", "Bearer " + meToken)
                        .header("Idempotency-Key", "rec-key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        Long id2 = com.jayway.jsonpath.JsonPath.<Number>read(
                r2.getResponse().getContentAsString(), "$.id").longValue();

        assertThat(id2).isEqualTo(id1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM schedule_proposal_occurrences WHERE proposal_id=?",
                Integer.class, id1)).isEqualTo(4);
    }

    @Test
    void 목록_summary와_상세에_정례_필드가_포함된다() throws Exception {
        MvcResult r = createRecurring(
                "{\"freq\":\"WEEKLY\",\"interval\":2,\"byDay\":[2,4],\"count\":4}");
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        Long id = com.jayway.jsonpath.JsonPath.<Number>read(
                r.getResponse().getContentAsString(), "$.id").longValue();

        // sent box summary — recurrence·occurrenceCount (계약 §3.7)
        mvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/api/scheduling/proposals?box=sent")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].occurrenceCount").value(4))
                .andExpect(jsonPath("$.content[0].recurrence.freq").value("WEEKLY"))
                .andExpect(jsonPath("$.content[0].recurrence.byDay")
                        .value(org.hamcrest.Matchers.contains(2, 4)));

        // 상세 — occurrences 전체 (interval 2: 5/12·5/14 같은 주 + 2주 뒤 5/26·5/28)
        mvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/api/scheduling/proposals/" + id)
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occurrences.length()").value(4))
                .andExpect(jsonPath("$.occurrences[0].date").value("2026-05-12"))
                .andExpect(jsonPath("$.occurrences[1].date").value("2026-05-14"))
                .andExpect(jsonPath("$.occurrences[2].date").value("2026-05-26"))
                .andExpect(jsonPath("$.occurrences[3].date").value("2026-05-28"))
                .andExpect(jsonPath("$.occurrences[1].startTime").value("14:00"));
    }

    private MvcResult respond(String token, Long id, String response) throws Exception {
        return mvc.perform(post("/api/scheduling/proposals/" + id + "/respond")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"" + response + "\"}"))
                .andReturn();
    }

    /** p1·p2 전원 수락까지 진행해 ALL_ACCEPTED 상태로 만든다. */
    private void acceptAll(Long id) throws Exception {
        respond(p1Token, id, "ACCEPTED");
        respond(p2Token, id, "ACCEPTED");
    }

    @Test
    void 전원_수락하면_ALL_ACCEPTED이고_방장에게만_확정대기_알림이_간다() throws Exception {
        Long id = createProposal();
        respond(p1Token, id, "ACCEPTED");
        mvc.perform(post("/api/scheduling/proposals/" + id + "/respond")
                        .header("Authorization", "Bearer " + p2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"ACCEPTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALL_ACCEPTED"));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id))
                .isEqualTo("ALL_ACCEPTED");
        // 게이트 — 확정 전이 없으므로 EVENTS·CONFIRMED 알림 없음 (ADR-0011)
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                .isEqualTo(0);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_CONFIRMED'", Integer.class))
                .isEqualTo(0);
        // 방장에게만 PROPOSAL_ALL_ACCEPTED — payload는 계약 §3.7 확정본 필드 집합
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_ALL_ACCEPTED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_ALL_ACCEPTED' AND user_id=?",
                Integer.class, meId)).isEqualTo(1);
        String payload = jdbc.queryForObject(
                "SELECT payload FROM notifications WHERE type='PROPOSAL_ALL_ACCEPTED'",
                String.class);
        assertThat(com.jayway.jsonpath.JsonPath.<Integer>read(payload, "$.proposalId")
                .longValue()).isEqualTo(id);
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(payload, "$.title"))
                .isEqualTo("캡스톤 회의");
        assertThat(com.jayway.jsonpath.JsonPath.<Integer>read(payload, "$.organizer.id")
                .longValue()).isEqualTo(meId);
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(payload, "$.organizer.nickname"))
                .isEqualTo("나");
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(payload, "$.slot.type"))
                .isEqualTo("TIME");
    }

    @Test
    void 방장이_confirm하면_CONFIRMED와_전원_events_알림이_단일_트랜잭션으로_생긴다() throws Exception {
        Long id = createProposal();
        acceptAll(id);

        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id))
                .isEqualTo("CONFIRMED");
        // 전원(주최자+참여자 2명) event — source='proposal' + 원 제안 연결 (계약 §3.4)
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal' AND proposal_id=?",
                Integer.class, id))
                .isEqualTo(3);
        // 알림: RECEIVED 2 + ALL_ACCEPTED 1 + CONFIRMED 3
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_RECEIVED'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_ALL_ACCEPTED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_CONFIRMED'", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void 비방장의_confirm은_404다() throws Exception {
        Long id = createProposal();
        acceptAll(id);

        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + p1Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROPOSAL-NOT-FOUND"));
    }

    @Test
    void PENDING_제안의_confirm은_409다() throws Exception {
        Long id = createProposal();
        respond(p1Token, id, "ACCEPTED");

        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL-NOT-AWAITING-CONFIRM"));
    }

    @Test
    void ALL_ACCEPTED에서_방장_취소는_CANCELLED다() throws Exception {
        Long id = createProposal();
        acceptAll(id);

        mvc.perform(post("/api/scheduling/proposals/" + id + "/cancel")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id))
                .isEqualTo("CANCELLED");
        // 취소 후 confirm은 게이트를 벗어났으므로 409
        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL-NOT-AWAITING-CONFIRM"));
    }

    @Test
    void 두_응답의_동시_호출은_FOR_UPDATE로_직렬화된다() throws Exception {
        Long id = createProposal();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<MvcResult> f1 = pool.submit(() -> {
            ready.countDown(); go.await();
            return respond(p1Token, id, "ACCEPTED");
        });
        Future<MvcResult> f2 = pool.submit(() -> {
            ready.countDown(); go.await();
            return respond(p2Token, id, "ACCEPTED");
        });
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        int s1 = f1.get(30, TimeUnit.SECONDS).getResponse().getStatus();
        int s2 = f2.get(30, TimeUnit.SECONDS).getResponse().getStatus();
        pool.shutdownNow();

        assertThat(List.of(s1, s2)).containsExactlyInAnyOrder(200, 200);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id))
                .isEqualTo("ALL_ACCEPTED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                .isEqualTo(0);
    }

    @Test
    void ALL_ACCEPTED_제안의_재응답은_409다() throws Exception {
        Long id = createProposal();
        respond(p1Token, id, "ACCEPTED");
        respond(p2Token, id, "ACCEPTED");

        mvc.perform(post("/api/scheduling/proposals/" + id + "/respond")
                        .header("Authorization", "Bearer " + p1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"DECLINED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL-NOT-PENDING"));
    }

    @Test
    void 거절하면_DECLINED이고_주최자에게_알림이_간다() throws Exception {
        Long id = createProposal();
        mvc.perform(post("/api/scheduling/proposals/" + id + "/respond")
                        .header("Authorization", "Bearer " + p1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"DECLINED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_DECLINED' AND user_id=?",
                Integer.class, meId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                .isEqualTo(0);
    }

    @Test
    void 비참여자의_응답은_403이다() throws Exception {
        Long id = createProposal();
        mvc.perform(post("/api/scheduling/proposals/" + id + "/respond")
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"ACCEPTED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROPOSAL-NOT-PARTICIPANT"));
    }

    @Test
    void 취소된_제안의_응답은_409다() throws Exception {
        Long id = createProposal();
        mvc.perform(post("/api/scheduling/proposals/" + id + "/cancel")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(post("/api/scheduling/proposals/" + id + "/respond")
                        .header("Authorization", "Bearer " + p1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"ACCEPTED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL-NOT-PENDING"));
    }

    @Test
    void 마지막_수락과_취소의_경합은_한쪽만_성공한다() throws Exception {
        Long id = createProposal();
        respond(p1Token, id, "ACCEPTED");
        // p2의 마지막 수락과 주최자 취소가 동시 경합
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<MvcResult> f1 = pool.submit(() -> {
            ready.countDown(); go.await();
            return respond(p2Token, id, "ACCEPTED");
        });
        Future<MvcResult> f2 = pool.submit(() -> {
            ready.countDown(); go.await();
            return mvc.perform(post("/api/scheduling/proposals/" + id + "/cancel")
                    .header("Authorization", "Bearer " + meToken)).andReturn();
        });
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        int s1 = f1.get(30, TimeUnit.SECONDS).getResponse().getStatus();
        int s2 = f2.get(30, TimeUnit.SECONDS).getResponse().getStatus();
        pool.shutdownNow();

        // 잠금 직렬화 — 취소는 PENDING·ALL_ACCEPTED 모두 허용이라 항상 성공,
        // 마지막 수락은 취소 선행 시 PROPOSAL-NOT-PENDING으로 409
        assertThat(s2).isEqualTo(200);
        assertThat(s1).isIn(200, 409);
        String status = jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id);
        assertThat(status).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                .isEqualTo(0);
    }

    @Test
    void 확정_알림_INSERT_실패_시_전이와_이벤트가_전부_롤백된다() throws Exception {
        Long id = createProposal();
        acceptAll(id);
        // PROPOSAL_CONFIRMED 발행만 실패 주입 — 2-인자 publish가 서비스 호출 경로
        // (내부 3-인자 self-invocation은 프록시를 우회해 2-인자를 스텁해야 가로챈다)
        org.mockito.Mockito.doThrow(new RuntimeException("injected publish failure"))
                .when(publisher).publish(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(
                                NotificationPayload.ProposalConfirmed.class));

        // confirm이 확정 경로를 타면 알림 실패로 500
        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isInternalServerError());

        // 단일 트랜잭션 증명 — status 전이·EVENTS INSERT 전부 롤백, 게이트 상태 유지
        assertThat(jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id))
                .isEqualTo("ALL_ACCEPTED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                .isEqualTo(0);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_CONFIRMED'",
                Integer.class)).isEqualTo(0);
    }

    @Test
    void ALL_ACCEPTED의_두_confirm_동시호출은_잠금으로_직렬화되어_한쪽만_성공한다()
            throws Exception {
        Long id = createProposal();
        acceptAll(id);
        // 같은 제안 행을 findByIdForUpdate로 잠그므로 패자는 ALL_ACCEPTED를 못 보고 409
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<MvcResult> f1 = pool.submit(() -> {
            ready.countDown(); go.await();
            return mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                    .header("Authorization", "Bearer " + meToken)).andReturn();
        });
        Future<MvcResult> f2 = pool.submit(() -> {
            ready.countDown(); go.await();
            return mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                    .header("Authorization", "Bearer " + meToken)).andReturn();
        });
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        MvcResult r1 = f1.get(30, TimeUnit.SECONDS);
        MvcResult r2 = f2.get(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        int s1 = r1.getResponse().getStatus();
        int s2 = r2.getResponse().getStatus();
        assertThat(List.of(s1, s2)).containsExactlyInAnyOrder(200, 409);
        MvcResult loser = s1 == 409 ? r1 : r2;
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(
                loser.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("PROPOSAL-NOT-AWAITING-CONFIRM");

        // 부산물 중복 없음 — EVENTS·CONFIRMED 알림이 참석자 수(3)만큼 정확히 한 번만
        assertThat(jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id))
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_CONFIRMED'",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_ALL_ACCEPTED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void ALL_ACCEPTED에서_confirm과_cancel의_경합은_승자가_최종상태를_결정한다() throws Exception {
        Long id = createProposal();
        acceptAll(id);
        // 같은 FOR UPDATE 잠금 — 선점 쪽이 상태를 바꾸고 패자는 409
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<MvcResult> fConfirm = pool.submit(() -> {
            ready.countDown(); go.await();
            return mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                    .header("Authorization", "Bearer " + meToken)).andReturn();
        });
        Future<MvcResult> fCancel = pool.submit(() -> {
            ready.countDown(); go.await();
            return mvc.perform(post("/api/scheduling/proposals/" + id + "/cancel")
                    .header("Authorization", "Bearer " + meToken)).andReturn();
        });
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        int sConfirm = fConfirm.get(30, TimeUnit.SECONDS).getResponse().getStatus();
        int sCancel = fCancel.get(30, TimeUnit.SECONDS).getResponse().getStatus();
        pool.shutdownNow();

        assertThat(List.of(sConfirm, sCancel)).containsExactlyInAnyOrder(200, 409);
        String status = jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id);
        if (sConfirm == 200) {
            // confirm 승리 — 확정 부산물이 정확히 한 번 생성되고 cancel은 게이트 이탈로 409
            assertThat(status).isEqualTo("CONFIRMED");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                    .isEqualTo(3);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_CONFIRMED'",
                    Integer.class)).isEqualTo(3);
        } else {
            // cancel 승리 — EVENTS·CONFIRMED 알림 미생성
            assertThat(status).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                    .isEqualTo(0);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_CONFIRMED'",
                    Integer.class)).isEqualTo(0);
        }
    }

    @Test
    void CONFIRMED_후_reconfirm은_409고_EVENTS와_알림이_늘지_않는다() throws Exception {
        Long id = createProposal();
        acceptAll(id);
        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk());

        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL-NOT-AWAITING-CONFIRM"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_CONFIRMED'",
                Integer.class)).isEqualTo(3);
    }

    @Test
    void CONFIRMED_후_참여자_재응답은_409다() throws Exception {
        Long id = createProposal();
        acceptAll(id);
        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk());

        mvc.perform(post("/api/scheduling/proposals/" + id + "/respond")
                        .header("Authorization", "Bearer " + p1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"DECLINED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL-NOT-PENDING"));
    }

    @Test
    void 두번째_확정알림_실패는_앞서_저장된_알림까지_전부_롤백한다() throws Exception {
        Long id = createProposal();
        acceptAll(id);
        // 첫 publish는 실제 INSERT까지 성공시키고 두 번째에서 실패 — "일부 알림 저장 후 롤백" 증명
        org.mockito.Mockito.doCallRealMethod()
                .doThrow(new RuntimeException("injected publish failure"))
                .when(publisher).publish(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(
                                NotificationPayload.ProposalConfirmed.class));

        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isInternalServerError());

        // 실제 경로를 탄 호출이 2번 — 첫 INSERT까지 수행 후 실패했음을 호출 수로 증명
        org.mockito.Mockito.verify(publisher, org.mockito.Mockito.times(2))
                .publish(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(
                                NotificationPayload.ProposalConfirmed.class));
        assertThat(jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id))
                .isEqualTo("ALL_ACCEPTED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                .isEqualTo(0);
        // 첫 publish가 INSERT까지 수행했지만 트랜잭션 롤백으로 0건이어야 한다
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_CONFIRMED'",
                Integer.class)).isEqualTo(0);
    }

    @Test
    void ALL_ACCEPTED_알림_실패는_마지막_응답과_상태전이를_롤백한다() throws Exception {
        Long id = createProposal();
        org.mockito.Mockito.doThrow(new RuntimeException("injected publish failure"))
                .when(publisher).publish(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(
                                NotificationPayload.ProposalAllAccepted.class));

        respond(p1Token, id, "ACCEPTED"); // 전원 수락 전이라 발행 경로를 타지 않아 200
        mvc.perform(post("/api/scheduling/proposals/" + id + "/respond")
                        .header("Authorization", "Bearer " + p2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"ACCEPTED\"}"))
                .andExpect(status().isInternalServerError());

        // p2의 응답 UPDATE와 ALL_ACCEPTED 전이가 함께 롤백 — 재시도 가능 상태로 남는다
        assertThat(jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT response FROM schedule_proposal_participants "
                        + "WHERE proposal_id=? AND user_id=?", String.class, id, p2Id))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_ALL_ACCEPTED'",
                Integer.class)).isEqualTo(0);
    }

    // ── confirm 재검증 + 회차별 이벤트 (spec §Confirm 재검증) ──

    /** 정례 제안(매주 화 14:00~16:00, count 4)을 만들어 ALL_ACCEPTED까지 진행해 id를 반환. */
    private Long acceptedRecurring() throws Exception {
        MvcResult r = createRecurring(
                "{\"freq\":\"WEEKLY\",\"interval\":1,\"byDay\":[2],\"count\":4}");
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        Long id = com.jayway.jsonpath.JsonPath.<Number>read(
                r.getResponse().getContentAsString(), "$.id").longValue();
        respond(p1Token, id, "ACCEPTED");
        respond(p2Token, id, "ACCEPTED");
        return id;
    }

    @Test
    void 참석자의_HARD_일정이_회차와_겹치면_409고_제안은_ALL_ACCEPTED를_유지한다()
            throws Exception {
        Long id = acceptedRecurring();
        // 회차 2 (2026-05-19 14:00~16:00)과 겹치는 HARD 이벤트
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,busy_policy,source)"
                + " VALUES (?,'겹침','2026-05-19 15:00:00','2026-05-19 17:00:00',"
                + "'HARD','manual')", p1Id);

        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL-CONFLICT"));

        // 원자성 — 상태·이벤트·알림 전부 확정 전 그대로
        assertThat(jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id))
                .isEqualTo("ALL_ACCEPTED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                .isEqualTo(0);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_CONFIRMED'",
                Integer.class)).isEqualTo(0);
    }

    @Test
    void 마지막_회차의_HARD_충돌도_전체가_원자적으로_실패한다() throws Exception {
        Long id = acceptedRecurring();
        // 회차 4 (2026-06-02 14:00~16:00)과만 겹침 — 앞 회차는 깨끗하지만 전부 롤백
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,busy_policy,source)"
                + " VALUES (?,'겹침','2026-06-02 14:30:00','2026-06-02 15:00:00',"
                + "'HARD','manual')", p2Id);

        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL-CONFLICT"));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id))
                .isEqualTo("ALL_ACCEPTED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                .isEqualTo(0);
    }

    @Test
    void 단일_제안도_HARD_겹침이면_409다() throws Exception {
        Long id = createProposal();
        acceptAll(id);
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,busy_policy,source)"
                + " VALUES (?,'겹침','2026-05-12 13:00:00','2026-05-12 14:00:01',"
                + "'HARD','manual')", meId);

        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL-CONFLICT"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                .isEqualTo(0);
    }

    @Test
    void SOFT_일정_겹침은_confirm을_막지_않는다() throws Exception {
        Long id = acceptedRecurring();
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,busy_policy,source)"
                + " VALUES (?,'소프트','2026-05-12 14:00:00','2026-05-12 16:00:00',"
                + "'SOFT','manual')", p1Id);

        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal' AND proposal_id=?",
                Integer.class, id)).isEqualTo(12);
    }

    @Test
    void FREE_일정_겹침은_confirm을_막지_않는다() throws Exception {
        Long id = acceptedRecurring();
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,busy_policy,source)"
                + " VALUES (?,'자유','2026-05-19 14:00:00','2026-05-19 16:00:00',"
                + "'FREE','manual')", p2Id);

        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void 회차와_맞닿는_HARD_일정은_충돌이_아니다() throws Exception {
        Long id = acceptedRecurring();
        // 반열린 구간 — end==occ.start라 겹치지 않는다
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,busy_policy,source)"
                + " VALUES (?,'맞닿음','2026-05-26 12:00:00','2026-05-26 14:00:00',"
                + "'HARD','manual')", meId);

        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void 정례_confirm은_참석자곱하기회차만큼_HARD_이벤트를_만든다() throws Exception {
        Long id = acceptedRecurring();

        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // 3명 × 4회차 — 모두 같은 proposal_id·HARD·TIME 모드(all_day=false)
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal' AND proposal_id=?",
                Integer.class, id)).isEqualTo(12);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal' AND proposal_id=?"
                        + " AND busy_policy='HARD' AND all_day=false",
                Integer.class, id)).isEqualTo(12);
        for (Long uid : List.of(meId, p1Id, p2Id)) {
            List<String> starts = jdbc.queryForList(
                    "SELECT start_time FROM events WHERE source='proposal'"
                            + " AND proposal_id=? AND user_id=? ORDER BY start_time",
                    String.class, id, uid);
            assertThat(starts).hasSize(4);
            assertThat(starts.get(0)).startsWith("2026-05-12 14:00");
            assertThat(starts.get(1)).startsWith("2026-05-19 14:00");
            assertThat(starts.get(2)).startsWith("2026-05-26 14:00");
            assertThat(starts.get(3)).startsWith("2026-06-02 14:00");
        }
        // 알림은 시리즈당 참석자 1회씩만
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_CONFIRMED'",
                Integer.class)).isEqualTo(3);
    }

    // ── 제안 라이프사이클 ↔ 채팅방 연동 (계약 §3.10) ──

    @Test
    void 참여자_2명_제안_생성은_채팅방과_전원_멤버를_만든다() throws Exception {
        Long id = createProposal();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_rooms WHERE proposal_id=? AND status='OPEN'",
                Integer.class, id)).isEqualTo(1);
        // 방장 포함 3명 — roster는 [me] + participantIds
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_room_members m JOIN chat_rooms r ON m.room_id=r.id "
                        + "WHERE r.proposal_id=?", Integer.class, id)).isEqualTo(3);
        for (Long uid : List.of(meId, p1Id, p2Id)) {
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM chat_room_members m JOIN chat_rooms r ON m.room_id=r.id "
                            + "WHERE r.proposal_id=? AND m.user_id=?",
                    Integer.class, id, uid)).isEqualTo(1);
        }
        // 슬롯 스냅샷 — TIME 모드, 저장 규약과 동일한 start/end
        assertThat(jdbc.queryForObject(
                "SELECT mode FROM chat_rooms WHERE proposal_id=?", String.class, id))
                .isEqualTo("TIME");
        assertThat(jdbc.queryForObject(
                "SELECT start_time FROM chat_rooms WHERE proposal_id=?", String.class, id))
                .startsWith("2026-05-12 14:00");
        assertThat(jdbc.queryForObject(
                "SELECT end_time FROM chat_rooms WHERE proposal_id=?", String.class, id))
                .startsWith("2026-05-12 16:00");
    }

    @Test
    void 참여자_1명_제안은_채팅방을_만들지_않는다() throws Exception {
        Long id = createProposal(List.of(p1Id));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_rooms WHERE proposal_id=?", Integer.class, id))
                .isEqualTo(0);
    }

    @Test
    void 참여자_3명_제안은_방장_포함_4명_멤버의_방을_만든다() throws Exception {
        Long id = createProposal(List.of(p1Id, p2Id, p3Id));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_room_members m JOIN chat_rooms r ON m.room_id=r.id "
                        + "WHERE r.proposal_id=?", Integer.class, id)).isEqualTo(4);
    }

    @Test
    void confirm하면_채팅방이_CLOSED되고_closed_at이_찍힌다() throws Exception {
        Long id = createProposal();
        acceptAll(id);

        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT status FROM chat_rooms WHERE proposal_id=?", String.class, id))
                .isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject(
                "SELECT closed_at FROM chat_rooms WHERE proposal_id=?", String.class, id))
                .isNotNull();
    }

    @Test
    void 거절로_DECLINED되면_채팅방이_CLOSED된다() throws Exception {
        Long id = createProposal();
        respond(p1Token, id, "DECLINED");

        assertThat(jdbc.queryForObject(
                "SELECT status FROM chat_rooms WHERE proposal_id=?", String.class, id))
                .isEqualTo("CLOSED");
    }

    @Test
    void PENDING_제안_취소는_채팅방을_CLOSED한다() throws Exception {
        Long id = createProposal();
        mvc.perform(post("/api/scheduling/proposals/" + id + "/cancel")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT status FROM chat_rooms WHERE proposal_id=?", String.class, id))
                .isEqualTo("CLOSED");
    }

    @Test
    void ALL_ACCEPTED_제안_취소도_채팅방을_CLOSED한다() throws Exception {
        Long id = createProposal();
        acceptAll(id);
        mvc.perform(post("/api/scheduling/proposals/" + id + "/cancel")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT status FROM chat_rooms WHERE proposal_id=?", String.class, id))
                .isEqualTo("CLOSED");
    }

    @Test
    void 이미_CLOSED인_방의_재close는_멱등이다() throws Exception {
        Long id = createProposal();
        mvc.perform(post("/api/scheduling/proposals/" + id + "/cancel")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk());
        String closedAt = jdbc.queryForObject(
                "SELECT closed_at FROM chat_rooms WHERE proposal_id=?", String.class, id);

        chatService.closeRoom(id); // 재호출 — 예외 없이 무시

        assertThat(jdbc.queryForObject(
                "SELECT status FROM chat_rooms WHERE proposal_id=?", String.class, id))
                .isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_rooms WHERE proposal_id=? AND closed_at=?",
                Integer.class, id, closedAt)).isEqualTo(1);
    }

    @Test
    void 방_없는_제안의_종결_경로는_close가_no_op이다() throws Exception {
        Long id = createProposal(List.of(p1Id)); // 1:1 — 방 미생성

        respond(p1Token, id, "DECLINED"); // closeRoom 호출 경로를 타도 방이 없어 no-op

        assertThat(jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id))
                .isEqualTo("DECLINED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_rooms WHERE proposal_id=?", Integer.class, id))
                .isEqualTo(0);
    }

    @Test
    void 방_생성_실패는_제안_참여자_방_멤버_알림을_전부_롤백한다() throws Exception {
        // 멤버 INSERT까지 수행한 뒤 예외 — 방 행 INSERT가 실제로 실행됐음을 보장하면서
        // 트랜잭션 롤백으로 방·멤버까지 사라지는지 증명한다 (계약 §3.10 단일 트랜잭션)
        org.mockito.Mockito.doAnswer(inv -> {
            inv.callRealMethod();
            throw new RuntimeException("injected createRoom failure");
        }).when(chatService).createRoom(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(app.weave.chat.domain.Mode.class),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList());

        mvc.perform(post("/api/scheduling/proposals")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"캡스톤 회의\",\"slot\":{\"type\":\"TIME\","
                                + "\"date\":\"2026-05-12\",\"startTime\":\"14:00\","
                                + "\"endTime\":\"16:00\"},\"participantIds\":["
                                + p1Id + "," + p2Id + "]}"))
                .andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM schedule_proposals", Integer.class)).isEqualTo(0);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM schedule_proposal_participants", Integer.class))
                .isEqualTo(0);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_rooms", Integer.class)).isEqualTo(0);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_room_members", Integer.class)).isEqualTo(0);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications", Integer.class)).isEqualTo(0);
    }

    @Test
    void confirm_알림실패는_제안상태와_방_CLOSED전이를_함께_롤백한다() throws Exception {
        Long id = createProposal();
        acceptAll(id);
        // closeRoom 이후 지점 — 두 번째 CONFIRMED 발행에서 실패 (첫 발행은 INSERT까지 수행)
        org.mockito.Mockito.doCallRealMethod()
                .doThrow(new RuntimeException("injected publish failure"))
                .when(publisher).publish(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(
                                NotificationPayload.ProposalConfirmed.class));

        mvc.perform(post("/api/scheduling/proposals/" + id + "/confirm")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isInternalServerError());

        // closeRoom의 status/closed_at UPDATE가 트랜잭션 롤백으로 복원된다
        assertThat(jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id))
                .isEqualTo("ALL_ACCEPTED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM chat_rooms WHERE proposal_id=?", String.class, id))
                .isEqualTo("OPEN");
        assertThat(jdbc.queryForObject(
                "SELECT closed_at FROM chat_rooms WHERE proposal_id=?", String.class, id))
                .isNull();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                .isEqualTo(0);
    }

    @Test
    void 전원_수락_직후에도_방은_게이트_기간_동안_OPEN을_유지한다() throws Exception {
        Long id = createProposal();
        acceptAll(id);

        // ALL_ACCEPTED 게이트 — confirm·cancel·decline 전까지 방은 살아있다 (계약 §3.10)
        assertThat(jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id))
                .isEqualTo("ALL_ACCEPTED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM chat_rooms WHERE proposal_id=?", String.class, id))
                .isEqualTo("OPEN");
        assertThat(jdbc.queryForObject(
                "SELECT closed_at FROM chat_rooms WHERE proposal_id=?", String.class, id))
                .isNull();
    }

    @Test
    void DATE_모드_제안의_방은_자정부터_endDate다음날_자정_배타상한으로_저장된다()
            throws Exception {
        MvcResult r = mvc.perform(post("/api/scheduling/proposals")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"주말 여행\",\"slot\":{\"type\":\"DATE\","
                                + "\"startDate\":\"2026-05-12\",\"endDate\":\"2026-05-14\"},"
                                + "\"participantIds\":[" + p1Id + "," + p2Id + "]}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = com.jayway.jsonpath.JsonPath.<Number>read(
                r.getResponse().getContentAsString(), "$.id").longValue();

        // 저장 규약: DATE는 startDate 자정 ~ endDate+1일 자정(배타 상한) — 방 스냅샷도 동일
        assertThat(jdbc.queryForObject(
                "SELECT mode FROM chat_rooms WHERE proposal_id=?", String.class, id))
                .isEqualTo("DATE");
        assertThat(jdbc.queryForObject(
                "SELECT start_time FROM chat_rooms WHERE proposal_id=?", String.class, id))
                .startsWith("2026-05-12 00:00");
        assertThat(jdbc.queryForObject(
                "SELECT end_time FROM chat_rooms WHERE proposal_id=?", String.class, id))
                .startsWith("2026-05-15 00:00");
    }

    @Test
    void 같은_멱등키로_재생성하면_같은_제안을_반환하고_방은_하나만_남는다() throws Exception {
        String body = "{\"title\":\"캡스톤 회의\",\"slot\":{\"type\":\"TIME\","
                + "\"date\":\"2026-05-12\",\"startTime\":\"14:00\",\"endTime\":\"16:00\"},"
                + "\"participantIds\":[" + p1Id + "," + p2Id + "]}";

        MvcResult r1 = mvc.perform(post("/api/scheduling/proposals")
                        .header("Authorization", "Bearer " + meToken)
                        .header("Idempotency-Key", "prop-key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Long id1 = com.jayway.jsonpath.JsonPath.<Number>read(
                r1.getResponse().getContentAsString(), "$.id").longValue();

        MvcResult r2 = mvc.perform(post("/api/scheduling/proposals")
                        .header("Authorization", "Bearer " + meToken)
                        .header("Idempotency-Key", "prop-key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Long id2 = com.jayway.jsonpath.JsonPath.<Number>read(
                r2.getResponse().getContentAsString(), "$.id").longValue();

        assertThat(id2).isEqualTo(id1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM schedule_proposals", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM schedule_proposal_participants", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_rooms WHERE proposal_id=?", Integer.class, id1))
                .isEqualTo(1);
    }
}
