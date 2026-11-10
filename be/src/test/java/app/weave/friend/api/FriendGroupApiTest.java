package app.weave.friend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FriendGroupApiTest {

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
    private Long f1Id;
    private Long outsiderId;
    private String meToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM share_replies");
        jdbc.update("DELETE FROM shares");
        jdbc.update("DELETE FROM feed_subscriptions");
        jdbc.update("DELETE FROM friend_group_members");
        jdbc.update("DELETE FROM friend_groups");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM friendships");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES "
                + "('me@weave.local','나','나','demo'),('f1@weave.local','일','일','demo'),"
                + "('out@weave.local','외','외','demo')");
        meId = idOf("me@weave.local");
        f1Id = idOf("f1@weave.local");
        outsiderId = idOf("out@weave.local");
        meToken = jwtProvider.createAccessToken(meId, List.of("ROLE_USER"));
        outsiderToken = jwtProvider.createAccessToken(outsiderId, List.of("ROLE_USER"));
        jdbc.update("INSERT INTO friendships(user_id,friend_id,status) "
                + "VALUES (?,?,'ACCEPTED')", meId, f1Id);
    }

    private Long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Long groupIdOf(MvcResult r) throws Exception {
        return com.jayway.jsonpath.JsonPath
                .<Number>read(r.getResponse().getContentAsString(), "$.groupId").longValue();
    }

    @Test
    void 그룹_CRUD와_멤버교체가_REST로_동작한다() throws Exception {
        MvcResult r = mvc.perform(post("/api/friend-groups")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"친한 친구\",\"memberIds\":[" + f1Id + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("친한 친구"))
                .andExpect(jsonPath("$.memberIds[0]").value(f1Id))
                .andReturn();
        Long groupId = groupIdOf(r);

        mvc.perform(get("/api/friend-groups")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(patch("/api/friend-groups/" + groupId)
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새 이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새 이름"));

        mvc.perform(put("/api/friend-groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberIds.length()").value(0));

        // 타인 그룹 접근 → 404
        mvc.perform(delete("/api/friend-groups/" + groupId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP-NOT-FOUND"));

        mvc.perform(delete("/api/friend-groups/" + groupId)
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void 그룹_에러는_계약_코드로_매핑된다() throws Exception {
        // 비친구 멤버 → 400 GROUP-MEMBER-INVALID
        mvc.perform(post("/api/friend-groups")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"memberIds\":[" + outsiderId + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP-MEMBER-INVALID"));

        // 4개째 → 409 GROUP-LIMIT-EXCEEDED
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/friend-groups")
                            .header("Authorization", "Bearer " + meToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"g" + i + "\"}"))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/friend-groups")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"g4\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP-LIMIT-EXCEEDED"));

        // 빈 이름 → 400
        mvc.perform(post("/api/friend-groups")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 피드구독_토글은_친구에게만_가능하다() throws Exception {
        // 구독 전 목록은 비어 있다
        mvc.perform(get("/api/friends/feed-subscriptions")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(put("/api/friends/" + f1Id + "/feed-subscription")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/friends/feed-subscriptions")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(f1Id));

        // 비친구 구독 → 400 SUBSCRIPTION-INVALID
        mvc.perform(put("/api/friends/" + outsiderId + "/feed-subscription")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION-INVALID"));

        mvc.perform(delete("/api/friends/" + f1Id + "/feed-subscription")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/friends/feed-subscriptions")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
