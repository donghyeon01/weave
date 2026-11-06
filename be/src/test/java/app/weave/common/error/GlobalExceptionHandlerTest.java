package app.weave.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 에러 봉투 계약(api-contract §1.4) 검증 — SecurityConfig 필터 단계 401은 Task 3이
 * entry point로 별도 처리하므로 여기서는 MVC 내부에서 던져진 예외만 다룬다.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = app.weave.auth.jwt.JwtAuthenticationFilter.class))
@Import(GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    public static class TestController {

        record EchoRequest(@NotBlank String title) {
        }

        @PostMapping("/test/echo")
        Map<String, String> echo(@Valid @RequestBody EchoRequest request) {
            return Map.of("echo", request.title());
        }

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(ErrorCode.FRIEND_DUPLICATE_REQUEST);
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("db connection refused");
        }

        @GetMapping("/test/denied")
        void denied() {
            throw new AccessDeniedException("not owner");
        }

        @GetMapping("/test/unauthorized")
        void unauthorized() {
            throw new BadCredentialsException("bad token");
        }
    }

    @Test
    void 검증_실패는_VALIDATION_봉투와_fieldErrors를_반환한다() throws Exception {
        mockMvc.perform(post("/test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("COMMON-VALIDATION"))
                .andExpect(jsonPath("$.traceId").isString())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").isString());
    }

    @Test
    void 비즈니스_예외는_의도된_code와_status로_매핑된다() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("FRIEND-DUPLICATE-REQUEST"))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void JSON_파싱_실패는_MALFORMED로_매핑된다() throws Exception {
        mockMvc.perform(post("/test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("COMMON-MALFORMED"));
    }

    @Test
    void MVC_내부_인증_예외도_401_봉투로_응답한다() throws Exception {
        mockMvc.perform(get("/test/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTH-UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void 권한_부족은_403_ACCESS_DENIED로_응답한다() throws Exception {
        mockMvc.perform(get("/test/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS-DENIED"));
    }

    @Test
    void 예상_밖_예외는_INTERNAL_ERROR_봉투로_응답하고_내부_메시지를_숨긴다() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL-ERROR"))
                .andExpect(jsonPath("$.traceId").isString())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.INTERNAL_ERROR.getMessage()));
    }
}
