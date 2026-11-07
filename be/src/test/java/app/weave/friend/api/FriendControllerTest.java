package app.weave.friend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.weave.auth.jwt.JwtProvider;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FriendControllerTest {

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
    private Long thirdId;
    private Long fourthId;
    private String meToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        // FK 참조 순으로 삭제 — events→proposals, participants→proposals·users
        jdbc.update("DELETE FROM schedule_proposal_participants");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM schedule_proposals");
        jdbc.update("DELETE FROM friendships");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES "
                + "('me@weave.local','나','나','demo'),('other@weave.local','타인','타인','demo'),"
                + "('third@weave.local','삼','삼','demo'),('fourth@weave.local','사','사','demo')");
        meId = jdbc.queryForObject("SELECT id FROM users WHERE email='me@weave.local'", Long.class);
        otherId = jdbc.queryForObject("SELECT id FROM users WHERE email='other@weave.local'", Long.class);
        thirdId = jdbc.queryForObject("SELECT id FROM users WHERE email='third@weave.local'", Long.class);
        fourthId = jdbc.queryForObject("SELECT id FROM users WHERE email='fourth@weave.local'", Long.class);
        meToken = jwtProvider.createAccessToken(meId, List.of("ROLE_USER"));
        otherToken = jwtProvider.createAccessToken(otherId, List.of("ROLE_USER"));
    }

    private MvcResult request(String token, String email) throws Exception {
        return mvc.perform(post("/api/friends/requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andReturn();
    }

    private Long friendshipIdOf(MvcResult r) throws Exception {
        return com.jayway.jsonpath.JsonPath
                .<Number>read(r.getResponse().getContentAsString(), "$.friendshipId").longValue();
    }

    @Test
    void 상호_요청_경합에서_역방향_PENDING이_정리된다() throws Exception {
        // A→B 요청 성공
        MvcResult r = request(meToken, "other@weave.local");
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        Long abId = friendshipIdOf(r);

        // B→A 요청은 기존 PENDING 때문에 DUPLICATE
        mvc.perform(post("/api/friends/requests")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"me@weave.local\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIEND-DUPLICATE-REQUEST"));

        // 역방향 PENDING을 직접 심고 B가 수락 → 역방향 행 삭제
        jdbc.update("INSERT INTO friendships(user_id,friend_id,status) VALUES (?,?,'PENDING')",
                otherId, meId);
        mvc.perform(post("/api/friends/requests/" + abId + "/accept")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM friendships", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void 양방향_동시_수락은_데드락_없이_한쪽만_성공한다() throws Exception {
        jdbc.update("INSERT INTO friendships(user_id,friend_id,status) VALUES (?,?,'PENDING'),(?,?,'PENDING')",
                meId, otherId, otherId, meId);
        Long abId = jdbc.queryForObject(
                "SELECT id FROM friendships WHERE user_id=? AND friend_id=?", Long.class, meId, otherId);
        Long baId = jdbc.queryForObject(
                "SELECT id FROM friendships WHERE user_id=? AND friend_id=?", Long.class, otherId, meId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        // me는 받은 요청(baId)을, other는 받은 요청(abId)을 동시에 수락
        Future<MvcResult> f1 = pool.submit(() -> {
            ready.countDown(); go.await();
            return mvc.perform(post("/api/friends/requests/" + baId + "/accept")
                    .header("Authorization", "Bearer " + meToken)).andReturn();
        });
        Future<MvcResult> f2 = pool.submit(() -> {
            ready.countDown(); go.await();
            return mvc.perform(post("/api/friends/requests/" + abId + "/accept")
                    .header("Authorization", "Bearer " + otherToken)).andReturn();
        });
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        int s1 = f1.get(30, TimeUnit.SECONDS).getResponse().getStatus();
        int s2 = f2.get(30, TimeUnit.SECONDS).getResponse().getStatus();
        pool.shutdownNow();

        // id 오름차순 잠금으로 직렬화 — 승자 200, 패자는 대상 행이 정리돼 404
        assertThat(List.of(s1, s2)).containsExactlyInAnyOrder(200, 404);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM friendships WHERE status='ACCEPTED'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM friendships", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void 발신자의_수락은_403_제3자는_404이다() throws Exception {
        Long abId = friendshipIdOf(request(meToken, "other@weave.local"));

        mvc.perform(post("/api/friends/requests/" + abId + "/accept")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS-DENIED"));
    }

    @Test
    void 이미_ACCEPTED_재수락은_FRIEND_ALREADY를_반환한다() throws Exception {
        Long abId = friendshipIdOf(request(meToken, "other@weave.local"));
        mvc.perform(post("/api/friends/requests/" + abId + "/accept")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());

        mvc.perform(post("/api/friends/requests/" + abId + "/accept")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIEND-ALREADY"));
    }

    @Test
    void 삭제_후에는_404를_반환한다() throws Exception {
        Long abId = friendshipIdOf(request(meToken, "other@weave.local"));

        mvc.perform(delete("/api/friends/" + abId)
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/friends/" + abId)
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FRIENDSHIP-NOT-FOUND"));
    }

    @Test
    void 친구_목록은_보낸_PENDING을_섞지_않는다() throws Exception {
        request(meToken, "other@weave.local");

        mvc.perform(get("/api/friends").header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/friends/requests/received")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].user.email").value("me@weave.local"));
        mvc.perform(get("/api/friends/requests/sent")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user.email").value("other@weave.local"));
    }

    @Test
    void 요청_메시지는_응답과_받은목록에_반영된다() throws Exception {
        mvc.perform(post("/api/friends/requests")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other@weave.local\",\"message\":\"같이 캡스톤해요\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("같이 캡스톤해요"))
                .andExpect(jsonPath("$.nextEvent").doesNotExist());

        mvc.perform(get("/api/friends/requests/received")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$[0].message").value("같이 캡스톤해요"));
    }

    @Test
    void 친구_목록의_nextEvent는_함께_확정된_가장_이른_미래일정이다() throws Exception {
        jdbc.update("INSERT INTO friendships(user_id,friend_id,status,message) "
                + "VALUES (?,?,'ACCEPTED','친구하자')", meId, otherId);
        // 친구가 주최자·본인이 참여자인 확정 제안 → 내 일정
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
        // 친구가 로스터에 없는 제안 일정(더 이른 시각)은 nextEvent에서 걸러져야 한다
        jdbc.update("INSERT INTO schedule_proposals(organizer_id,title,mode,start_time,end_time,status) "
                + "VALUES (?,'관련없음','TIME','2027-05-19 10:00:00','2027-05-19 11:00:00','CONFIRMED')",
                thirdId);
        Long pid2 = jdbc.queryForObject(
                "SELECT id FROM schedule_proposals ORDER BY id DESC LIMIT 1", Long.class);
        jdbc.update("INSERT INTO schedule_proposal_participants(proposal_id,user_id,response) "
                + "VALUES (?,?,'ACCEPTED')", pid2, meId);
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,source,proposal_id) "
                + "VALUES (?,'관련없음','2027-05-19 10:00:00','2027-05-19 11:00:00','proposal',?)",
                meId, pid2);

        mvc.perform(get("/api/friends").header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].message").value("친구하자"))
                .andExpect(jsonPath("$[0].nextEvent.title").value("약속"))
                .andExpect(jsonPath("$[0].nextEvent.startTime").value("2027-05-20T14:00"));
    }

    @Test
    void 친구_일정은_조율불가_블록만_반환한다() throws Exception {
        jdbc.update("INSERT INTO friendships(user_id,friend_id,status) VALUES (?,?,'ACCEPTED')",
                meId, otherId);
        Long fid = jdbc.queryForObject(
                "SELECT id FROM friendships WHERE user_id=? AND friend_id=?", Long.class,
                meId, otherId);
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time) "
                + "VALUES (?,'친구회의','2027-05-20 10:00:00','2027-05-20 11:00:00'),"
                + "(?,'범위밖','2027-06-01 10:00:00','2027-06-01 11:00:00')", otherId, otherId);

        // 제목·내용 등 메타는 어떤 필드도 노출하지 않는다 (계약 §3.6)
        mvc.perform(get("/api/friends/" + fid + "/schedule")
                        .param("start", "2027-05-01").param("end", "2027-05-31")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks.length()").value(1))
                .andExpect(jsonPath("$.blocks[0].startTime").value("2027-05-20T10:00"))
                .andExpect(jsonPath("$.blocks[0].endTime").value("2027-05-20T11:00"))
                .andExpect(jsonPath("$.blocks[0].title").doesNotExist())
                .andExpect(jsonPath("$.blocks[0].id").doesNotExist());
    }

    @Test
    void PENDING이나_타인_관계의_schedule은_404이다() throws Exception {
        Long pendingId = friendshipIdOf(request(meToken, "third@weave.local"));
        mvc.perform(get("/api/friends/" + pendingId + "/schedule")
                        .param("start", "2027-05-01").param("end", "2027-05-31")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FRIENDSHIP-NOT-FOUND"));

        // third↔fourth 관계 — 나와 무관한 행도 404 (존재 비노출)
        jdbc.update("INSERT INTO friendships(user_id,friend_id,status) VALUES (?,?,'ACCEPTED')",
                thirdId, fourthId);
        Long foreignId = jdbc.queryForObject(
                "SELECT id FROM friendships WHERE user_id=? AND friend_id=?", Long.class,
                thirdId, fourthId);
        mvc.perform(get("/api/friends/" + foreignId + "/schedule")
                        .param("start", "2027-05-01").param("end", "2027-05-31")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FRIENDSHIP-NOT-FOUND"));
    }
}
