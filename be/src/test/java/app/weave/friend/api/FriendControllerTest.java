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
    private String meToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM friendships");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM users WHERE email IN ('me@weave.local','other@weave.local')");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES ('me@weave.local','나','나','demo'),('other@weave.local','타인','타인','demo')");
        meId = jdbc.queryForObject("SELECT id FROM users WHERE email='me@weave.local'", Long.class);
        otherId = jdbc.queryForObject("SELECT id FROM users WHERE email='other@weave.local'", Long.class);
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
}
