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

    private Long meId;
    private Long p1Id;
    private Long p2Id;
    private Long outsiderId;
    private String meToken;
    private String p1Token;
    private String p2Token;
    private String outsiderToken;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM schedule_proposal_participants");
        jdbc.update("DELETE FROM schedule_proposals");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM friendships");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES "
                + "('me@weave.local','나','나','demo'),('p1@weave.local','일','일','demo'),"
                + "('p2@weave.local','이','이','demo'),('out@weave.local','외','외','demo')");
        meId = idOf("me@weave.local");
        p1Id = idOf("p1@weave.local");
        p2Id = idOf("p2@weave.local");
        outsiderId = idOf("out@weave.local");
        meToken = jwtProvider.createAccessToken(meId, List.of("ROLE_USER"));
        p1Token = jwtProvider.createAccessToken(p1Id, List.of("ROLE_USER"));
        p2Token = jwtProvider.createAccessToken(p2Id, List.of("ROLE_USER"));
        outsiderToken = jwtProvider.createAccessToken(outsiderId, List.of("ROLE_USER"));
        befriend(meId, p1Id);
        befriend(meId, p2Id);
    }

    private Long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    }

    private void befriend(Long a, Long b) {
        jdbc.update("INSERT INTO friendships(user_id,friend_id,status) VALUES (?,?,'ACCEPTED')",
                a, b);
    }

    private Long createProposal() throws Exception {
        MvcResult r = mvc.perform(post("/api/scheduling/proposals")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"캡스톤 회의\",\"slot\":{\"type\":\"TIME\",\"date\":\"2026-05-12\",\"startTime\":\"14:00\",\"endTime\":\"16:00\"},\"participantIds\":["
                                + p1Id + "," + p2Id + "]}"))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Location",
                                org.hamcrest.Matchers.startsWith("/api/scheduling/proposals/")))
                .andReturn();
        return com.jayway.jsonpath.JsonPath.<Number>read(
                r.getResponse().getContentAsString(), "$.id").longValue();
    }

    private MvcResult respond(String token, Long id, String response) throws Exception {
        return mvc.perform(post("/api/scheduling/proposals/" + id + "/respond")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"" + response + "\"}"))
                .andReturn();
    }

    @Test
    void 전원_수락하면_CONFIRMED와_전원_events_알림이_단일_트랜잭션으로_생긴다() throws Exception {
        Long id = createProposal();
        respond(p1Token, id, "ACCEPTED");
        mvc.perform(post("/api/scheduling/proposals/" + id + "/respond")
                        .header("Authorization", "Bearer " + p2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"ACCEPTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id))
                .isEqualTo("CONFIRMED");
        // 전원(주최자+참여자 2명) event — source='proposal'
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                .isEqualTo(3);
        // 알림: RECEIVED 2 + CONFIRMED 3
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_RECEIVED'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_CONFIRMED'", Integer.class))
                .isEqualTo(3);
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
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void 확정된_제안의_재응답은_409다() throws Exception {
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

        // 잠금 직렬화 — 한쪽만 전이 성공, 패자는 409
        assertThat(List.of(s1, s2)).containsExactlyInAnyOrder(200, 409);
        String status = jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id);
        assertThat(status).isIn("CONFIRMED", "CANCELLED");
        if (status.equals("CONFIRMED")) {
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                    .isEqualTo(3);
        } else {
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                    .isEqualTo(0);
        }
    }

    @Test
    void 확정_알림_INSERT_실패_시_응답과_이벤트가_전부_롤백된다() throws Exception {
        Long id = createProposal();
        // PROPOSAL_CONFIRMED 발행만 실패 주입 — 2-인자 publish가 서비스 호출 경로
        // (내부 3-인자 self-invocation은 프록시를 우회해 2-인자를 스텁해야 가로챈다)
        org.mockito.Mockito.doThrow(new RuntimeException("injected publish failure"))
                .when(publisher).publish(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(
                                NotificationPayload.ProposalConfirmed.class));

        respond(p1Token, id, "ACCEPTED");
        // 마지막 수락이 확정 경로를 타면 알림 실패로 500
        mvc.perform(post("/api/scheduling/proposals/" + id + "/respond")
                        .header("Authorization", "Bearer " + p2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"ACCEPTED\"}"))
                .andExpect(status().isInternalServerError());

        // 단일 트랜잭션 증명 — 응답 UPDATE·status 전이·EVENTS INSERT 전부 롤백
        assertThat(jdbc.queryForObject(
                "SELECT status FROM schedule_proposals WHERE id=?", String.class, id))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT response FROM schedule_proposal_participants WHERE proposal_id=? AND user_id=?",
                String.class, id, p2Id)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE source='proposal'", Integer.class))
                .isEqualTo(0);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type='PROPOSAL_CONFIRMED'",
                Integer.class)).isEqualTo(0);
    }
}
