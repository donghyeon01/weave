package app.weave.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"weave.demo-enabled=true"})
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

    @Autowired
    MockMvc mvc;

    /** IP별 rate limit 격리 — 테스트마다 고유 IP를 쓴다. */
    private static RequestPostProcessor remoteIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private Cookie demoLogin(String ip) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/demo").with(remoteIp(ip))
                        .header("User-Agent", "test-agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("demo@weave.local"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().path("refresh_token", "/api/auth"))
                .andReturn();
        return result.getResponse().getCookie("refresh_token");
    }

    @Test
    void 데모_로그인은_200과_refresh_쿠키를_반환한다() throws Exception {
        demoLogin("10.0.0.1");
    }

    @Test
    void refresh는_새_access와_회전된_refresh를_반환한다() throws Exception {
        Cookie refresh = demoLogin("10.0.0.2");

        mvc.perform(post("/api/auth/refresh")
                        .cookie(refresh)
                        .header("User-Agent", "test-agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(cookie().exists("refresh_token"));
    }

    @Test
    void 구_refresh_재사용은_401_REUSED를_반환한다() throws Exception {
        Cookie refresh = demoLogin("10.0.0.3");

        mvc.perform(post("/api/auth/refresh").cookie(refresh)
                        .header("User-Agent", "test-agent"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/refresh").cookie(refresh)
                        .header("User-Agent", "test-agent"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-REFRESH-REUSED"));
    }

    @Test
    void 데모_로그인_11번째_호출은_429를_반환한다() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/demo").with(remoteIp("10.0.0.4"))
                            .header("User-Agent", "test-agent"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/auth/demo").with(remoteIp("10.0.0.4"))
                        .header("User-Agent", "test-agent"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("DEMO-RATE-LIMITED"));
    }

    @Test
    void users_me는_데모_로그인_토큰으로_200을_반환한다() throws Exception {
        MvcResult login = mvc.perform(post("/api/auth/demo").with(remoteIp("10.0.0.5"))
                        .header("User-Agent", "test-agent"))
                .andExpect(status().isOk()).andReturn();
        String accessToken = com.jayway.jsonpath.JsonPath
                .read(login.getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("demo@weave.local"));
    }
}
