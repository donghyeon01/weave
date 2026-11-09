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
}
