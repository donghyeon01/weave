package app.weave.event.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    private String token;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM users WHERE email = 'me@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES ('me@weave.local','나','나','demo')");
        meId = jdbc.queryForObject("SELECT id FROM users WHERE email='me@weave.local'", Long.class);
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
}
