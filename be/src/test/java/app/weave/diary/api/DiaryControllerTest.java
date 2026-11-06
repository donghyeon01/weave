package app.weave.diary.api;

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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DiaryControllerTest {

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
        jdbc.update("DELETE FROM diaries");
        jdbc.update("DELETE FROM users WHERE email = 'me@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES ('me@weave.local','나','나','demo')");
        meId = jdbc.queryForObject("SELECT id FROM users WHERE email='me@weave.local'", Long.class);
        token = jwtProvider.createAccessToken(meId, List.of("ROLE_USER"));
    }

    @Test
    void 동일_date_재생성은_409를_반환한다() throws Exception {
        String body = "{\"date\":\"2026-05-20\",\"title\":\"첫 기록\",\"content\":\"오늘의 일기\"}";
        mvc.perform(post("/api/diaries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/diaries/2026-05-20"));

        mvc.perform(post("/api/diaries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DIARY-DUPLICATE"));
    }

    @Test
    void 월_목록은_content_없이_date_title만_반환한다() throws Exception {
        mvc.perform(post("/api/diaries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-05-20\",\"title\":\"제목\",\"content\":\"비밀 내용\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/diaries?yearMonth=2026-05")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value("2026-05-20"))
                .andExpect(jsonPath("$[0].title").value("제목"))
                .andExpect(jsonPath("$[0].content").doesNotExist());
    }

    @Test
    void 월_경계는_배타_상한으로_정확히_나뉜다() throws Exception {
        jdbc.update("INSERT INTO diaries(user_id,date,title,content) VALUES (?,'2026-05-31','말일','본문'),(?,'2026-06-01','익월','본문')",
                meId, meId);

        mvc.perform(get("/api/diaries?yearMonth=2026-05")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value("2026-05-31"));

        mvc.perform(get("/api/diaries?yearMonth=2026-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value("2026-06-01"));
    }

    @Test
    void mood_tags는_생성_조회_수정에서_라운드트립된다() throws Exception {
        mvc.perform(post("/api/diaries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-05-20\",\"content\":\"본문\",\"mood\":\"happy\",\"tags\":[\"연남동\",\"맛집탐방\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mood").value("happy"))
                .andExpect(jsonPath("$.tags[0]").value("연남동"));

        mvc.perform(get("/api/diaries/2026-05-20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mood").value("happy"))
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.tags[1]").value("맛집탐방"));

        mvc.perform(patch("/api/diaries/2026-05-20")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mood\":\"calm\",\"tags\":[\"서울\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mood").value("calm"))
                .andExpect(jsonPath("$.tags[0]").value("서울"));
    }

    @Test
    void 없는_date는_404_기록_없음을_반환하고_삭제는_204이다() throws Exception {
        mvc.perform(get("/api/diaries/2026-05-20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DIARY-NOT-FOUND"));

        mvc.perform(post("/api/diaries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-05-20\",\"content\":\"본문\"}"))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/diaries/2026-05-20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }
}
