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
        // stranger는 친구 아님 — 대체 대상 아님
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60,\"windowStart\":\"09:00\",\"windowEnd\":\"11:00\"}"))
                .andExpect(status().isOk())
                // me+friend 2명이 전원 — stranger는 참여자가 아니다
                .andExpect(jsonPath("$.candidates[0].totalCount").value(2))
                .andExpect(jsonPath("$.candidates[0].percent").value(100));
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
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time,all_day,source) VALUES (?,'종일','2026-05-12 00:00:00','2026-05-12 23:59:00',true,'manual')",
                friendId);
        mvc.perform(post("/api/scheduling")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2026-05-12\",\"rangeEnd\":\"2026-05-12\",\"slotMinutes\":60}"))
                .andExpect(status().isOk())
                // 친구가 종일 busy → 전원 가능 슬롯 없음
                .andExpect(jsonPath("$.candidates[*].percent").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.lessThan(100))));
    }
}
