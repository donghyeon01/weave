package app.weave.task.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class TaskControllerTest {

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
        jdbc.update("DELETE FROM tasks");
        jdbc.update("DELETE FROM users WHERE email IN ('me@weave.local','other@weave.local')");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES ('me@weave.local','나','나','demo'),('other@weave.local','타인','타인','demo')");
        meId = jdbc.queryForObject("SELECT id FROM users WHERE email='me@weave.local'", Long.class);
        otherId = jdbc.queryForObject("SELECT id FROM users WHERE email='other@weave.local'", Long.class);
        token = jwtProvider.createAccessToken(meId, List.of("ROLE_USER"));
    }

    @Test
    void 생성은_201과_Location을_반환한다() throws Exception {
        mvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"리포트\",\"dueDate\":\"2026-05-20\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/tasks/")))
                .andExpect(jsonPath("$.title").value("리포트"))
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void 멱등키_재요청은_같은_id를_반환한다() throws Exception {
        String body = "{\"title\":\"중복요청\",\"dueDate\":\"2026-05-21\"}";
        MvcResult first = mvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        Number firstId = com.jayway.jsonpath.JsonPath
                .read(first.getResponse().getContentAsString(), "$.id");

        MvcResult second = mvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        Number secondId = com.jayway.jsonpath.JsonPath
                .read(second.getResponse().getContentAsString(), "$.id");

        assertThat(secondId.longValue()).isEqualTo(firstId.longValue());
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tasks WHERE user_id=?", Integer.class, meId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void 타인_소유_id는_404를_반환한다() throws Exception {
        jdbc.update("INSERT INTO tasks(user_id,title,due_date) VALUES (?,'타인할일','2026-05-20')",
                otherId);
        Long otherTaskId = jdbc.queryForObject(
                "SELECT id FROM tasks WHERE user_id=? LIMIT 1", Long.class, otherId);

        mvc.perform(patch("/api/tasks/" + otherTaskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK-NOT-FOUND"));
    }

    @Test
    void 완료_토글과_목록을_검증한다() throws Exception {
        MvcResult created = mvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"토글\"}"))
                .andExpect(status().isCreated()).andReturn();
        Number id = com.jayway.jsonpath.JsonPath
                .read(created.getResponse().getContentAsString(), "$.id");

        mvc.perform(patch("/api/tasks/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));

        mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
