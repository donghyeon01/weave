package app.weave.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class NotificationControllerTest {

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
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM users WHERE email IN ('me@weave.local','other@weave.local')");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) "
                + "VALUES ('me@weave.local','나','나','demo'),('other@weave.local','타인','타인','demo')");
        meId = jdbc.queryForObject("SELECT id FROM users WHERE email='me@weave.local'", Long.class);
        otherId = jdbc.queryForObject(
                "SELECT id FROM users WHERE email='other@weave.local'", Long.class);
        token = jwtProvider.createAccessToken(meId, List.of("ROLE_USER"));
    }

    private void insertNotification(Long userId, String type, String payload,
            String readAt, String createdAt) {
        jdbc.update("INSERT INTO notifications(user_id,type,payload,read_at,created_at) "
                + "VALUES (?,?,?,?,?)", userId, type, payload, readAt, createdAt);
    }

    @Test
    void 목록은_미읽음_우선이고_같은_상태끼리는_createdAt_내림차순이다() throws Exception {
        // 읽음 1건 + 미읽음 2건(createdAt 역순 배치) + 타인 알림 1건
        insertNotification(meId, "FRIEND_ACCEPTED",
                "{\"friendshipId\":1,\"fromUser\":{\"id\":2,\"nickname\":\"영희\"}}",
                "2026-11-01 12:00:00", "2026-11-01 08:00:00");
        insertNotification(meId, "PROPOSAL_RECEIVED",
                "{\"proposalId\":9,\"title\":\"회의\",\"organizer\":{\"id\":3,\"nickname\":\"철수\"},"
                        + "\"slot\":{\"type\":\"TIME\",\"date\":\"2026-05-12\",\"startTime\":\"14:00\",\"endTime\":\"16:00\"}}",
                null, "2026-11-01 09:00:00");
        insertNotification(meId, "FRIEND_REQUEST",
                "{\"friendshipId\":2,\"fromUser\":{\"id\":4,\"nickname\":\"민수\"}}",
                null, "2026-11-01 10:00:00");
        insertNotification(otherId, "FRIEND_REQUEST",
                "{\"friendshipId\":3,\"fromUser\":{\"id\":5,\"nickname\":\"지연\"}}",
                null, "2026-11-01 11:00:00");

        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].type").value("FRIEND_REQUEST"))
                .andExpect(jsonPath("$.content[0].readAt").value(nullValue()))
                .andExpect(jsonPath("$.content[1].type").value("PROPOSAL_RECEIVED"))
                .andExpect(jsonPath("$.content[1].readAt").value(nullValue()))
                .andExpect(jsonPath("$.content[2].type").value("FRIEND_ACCEPTED"))
                .andExpect(jsonPath("$.content[2].readAt").isNotEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.unreadCount").value(2));
    }

    @Test
    void 본인_알림_read는_readAt을_기록한다() throws Exception {
        insertNotification(meId, "FRIEND_REQUEST",
                "{\"friendshipId\":2,\"fromUser\":{\"id\":4,\"nickname\":\"민수\"}}",
                null, "2026-11-01 10:00:00");
        Long id = jdbc.queryForObject(
                "SELECT id FROM notifications WHERE user_id = ?", Long.class, meId);

        mvc.perform(patch("/api/notifications/{id}/read", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.readAt").isNotEmpty());
    }

    @Test
    void 타인_알림_read는_404를_반환한다() throws Exception {
        insertNotification(otherId, "FRIEND_REQUEST",
                "{\"friendshipId\":3,\"fromUser\":{\"id\":5,\"nickname\":\"지연\"}}",
                null, "2026-11-01 10:00:00");
        Long otherNotificationId = jdbc.queryForObject(
                "SELECT id FROM notifications WHERE user_id = ?", Long.class, otherId);

        mvc.perform(patch("/api/notifications/{id}/read", otherNotificationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION-NOT-FOUND"));
    }

    @Test
    void read_all은_본인_미읽음만_전부_읽음_처리한다() throws Exception {
        insertNotification(meId, "FRIEND_REQUEST",
                "{\"friendshipId\":2,\"fromUser\":{\"id\":4,\"nickname\":\"민수\"}}",
                null, "2026-11-01 09:00:00");
        insertNotification(meId, "PROPOSAL_CONFIRMED",
                "{\"proposalId\":9,\"title\":\"회의\",\"slot\":{\"type\":\"DATE\",\"startDate\":\"2026-05-12\",\"endDate\":\"2026-05-14\"}}",
                null, "2026-11-01 10:00:00");
        insertNotification(meId, "FRIEND_ACCEPTED",
                "{\"friendshipId\":1,\"fromUser\":{\"id\":2,\"nickname\":\"영희\"}}",
                "2026-11-01 12:00:00", "2026-11-01 08:00:00");
        insertNotification(otherId, "FRIEND_REQUEST",
                "{\"friendshipId\":3,\"fromUser\":{\"id\":5,\"nickname\":\"지연\"}}",
                null, "2026-11-01 11:00:00");

        mvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2));

        Integer myUnread = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_at IS NULL",
                Integer.class, meId);
        Integer otherUnread = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_at IS NULL",
                Integer.class, otherId);
        assertThat(myUnread).isZero();
        assertThat(otherUnread).isEqualTo(1); // 타인 미읽음은 영향 없음
    }
}
