package app.weave.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.weave.auth.jwt.JwtProvider;
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

import java.util.List;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserControllerTest {

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
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users WHERE email IN ('me@weave.local','other@weave.local')");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES ('me@weave.local','나','나','demo'),('other@weave.local','타인','타인','demo')");
        meId = jdbc.queryForObject("SELECT id FROM users WHERE email='me@weave.local'", Long.class);
        token = jwtProvider.createAccessToken(meId, List.of("ROLE_USER"));
    }

    @Test
    void 본인_이메일_검색은_결과에서_제외된다() throws Exception {
        mvc.perform(get("/api/users/search").param("email", "me@weave.local")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 타인_이메일_검색은_1건을_반환한다() throws Exception {
        mvc.perform(get("/api/users/search").param("email", "other@weave.local")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("other@weave.local"));
    }

    @Test
    void 닉네임_1자는_400을_반환한다() throws Exception {
        mvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-VALIDATION"));
    }

    @Test
    void me는_bio_theme_share_플래그_기본값을_반환한다() throws Exception {
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("system"))
                .andExpect(jsonPath("$.shareBusyOnly").value(true))
                .andExpect(jsonPath("$.shareTitles").value(false))
                .andExpect(jsonPath("$.shareDetails").value(false));
    }

    @Test
    void 프로필_설정_부분수정이_반영된다() throws Exception {
        mvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새닉\",\"bio\":\"안녕\",\"theme\":\"dark\","
                                + "\"shareBusyOnly\":false,\"shareTitles\":true,"
                                + "\"shareDetails\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새닉"))
                .andExpect(jsonPath("$.bio").value("안녕"))
                .andExpect(jsonPath("$.theme").value("dark"))
                .andExpect(jsonPath("$.shareBusyOnly").value(false))
                .andExpect(jsonPath("$.shareTitles").value(true))
                .andExpect(jsonPath("$.shareDetails").value(true));

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.bio").value("안녕"))
                .andExpect(jsonPath("$.theme").value("dark"));
    }

    @Test
    void enum_외_theme은_400을_반환한다() throws Exception {
        mvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"neon\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-VALIDATION"));
    }

    @Test
    void 탈퇴하면_세션과_사용자가_삭제되고_쿠키가_만료된다() throws Exception {
        Long otherId = jdbc.queryForObject(
                "SELECT id FROM users WHERE email='other@weave.local'", Long.class);
        jdbc.update("INSERT INTO refresh_tokens(user_id,token_hash,user_agent,expiry_date) "
                + "VALUES (?, 'abc123hash', 'ua', DATE_ADD(NOW(), INTERVAL 14 DAY))", meId);
        jdbc.update("INSERT INTO refresh_tokens(user_id,token_hash,user_agent,expiry_date) "
                + "VALUES (?, 'def456hash', 'ua', DATE_ADD(NOW(), INTERVAL 14 DAY))", otherId);

        mvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .cookie().maxAge("refresh_token", 0));

        Integer myRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, meId);
        Integer otherRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, otherId);
        assertThat(myRows).isZero();
        assertThat(otherRows).isEqualTo(1); // 타인 데이터는 무결
        Integer userRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, meId);
        assertThat(userRows).isZero();
    }
}
