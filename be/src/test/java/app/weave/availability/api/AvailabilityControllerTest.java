package app.weave.availability.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class AvailabilityControllerTest {

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
    private String otherToken;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM availability_blocks");
        jdbc.update("DELETE FROM availability_scopes");
        jdbc.update("DELETE FROM availability_preferences");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES "
                + "('me@weave.local','나','나','demo'),('other@weave.local','타인','타인','demo')");
        meId = jdbc.queryForObject("SELECT id FROM users WHERE email='me@weave.local'", Long.class);
        otherId = jdbc.queryForObject("SELECT id FROM users WHERE email='other@weave.local'",
                Long.class);
        token = jwtProvider.createAccessToken(meId, List.of("ROLE_USER"));
        otherToken = jwtProvider.createAccessToken(otherId, List.of("ROLE_USER"));
    }

    private Long createScope(String mode, String start, String end) throws Exception {
        String location = mvc.perform(post("/api/availability/scopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"" + mode + "\",\"rangeStart\":\"" + start
                                + "\",\"rangeEnd\":\"" + end + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void 범위_생성은_201과_INPUT_상태_revision0을_반환한다() throws Exception {
        mvc.perform(post("/api/availability/scopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2027-03-10\","
                                + "\"rangeEnd\":\"2027-03-12\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.matchesRegex("/api/availability/scopes/\\d+")))
                .andExpect(jsonPath("$.mode").value("TIME"))
                .andExpect(jsonPath("$.rangeStart").value("2027-03-10"))
                .andExpect(jsonPath("$.rangeEnd").value("2027-03-12"))
                .andExpect(jsonPath("$.status").value("INPUT"))
                .andExpect(jsonPath("$.revision").value(0))
                .andExpect(jsonPath("$.blocks.length()").value(0))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void 같은_mode의_활성_범위와_겹치면_409다() throws Exception {
        createScope("TIME", "2027-03-10", "2027-03-12");

        mvc.perform(post("/api/availability/scopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2027-03-12\","
                                + "\"rangeEnd\":\"2027-03-15\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AVAILABILITY-SCOPE-CONFLICT"));
    }

    @Test
    void 다른_mode는_같은_기간에_공존할_수_있다() throws Exception {
        createScope("TIME", "2027-03-10", "2027-03-12");

        mvc.perform(post("/api/availability/scopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DATE\",\"rangeStart\":\"2027-03-10\","
                                + "\"rangeEnd\":\"2027-03-12\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void 기간_역전은_AVAILABILITY_INVALID_400이다() throws Exception {
        mvc.perform(post("/api/availability/scopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2027-03-12\","
                                + "\"rangeEnd\":\"2027-03-10\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVAILABILITY-INVALID"));
    }

    @Test
    void mode_enum_외_값은_COMMON_VALIDATION_400이다() throws Exception {
        mvc.perform(post("/api/availability/scopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"WEEK\",\"rangeStart\":\"2027-03-10\","
                                + "\"rangeEnd\":\"2027-03-12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-VALIDATION"));
    }

    @Test
    void 목록은_from_to_겹침_필터를_적용하고_blockCount를_포함한다() throws Exception {
        Long a = createScope("TIME", "2027-03-01", "2027-03-05");
        createScope("TIME", "2027-04-01", "2027-04-05");
        createScope("DATE", "2027-03-03", "2027-03-10");
        // a에 블록 1개
        mvc.perform(put("/api/availability/scopes/" + a + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[{\"kind\":\"TIME\","
                                + "\"startAt\":\"2027-03-02T10:00:00\","
                                + "\"endAt\":\"2027-03-02T12:00:00\"}],"
                                + "\"expectedRevision\":0}"))
                .andExpect(status().isOk());

        // 필터 없음 — 전체 3개
        mvc.perform(get("/api/availability/scopes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        // from만 — rangeEnd >= from 인 것만 (3월 TIME + DATE)
        mvc.perform(get("/api/availability/scopes?from=2027-03-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // to만 — rangeStart <= to 인 것만 (3월 TIME + DATE)
        mvc.perform(get("/api/availability/scopes?to=2027-03-31")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id==" + a + ")].blockCount").value(
                        org.hamcrest.Matchers.contains(1)));

        // 양쪽 — 겹치는 것만
        mvc.perform(get("/api/availability/scopes?from=2027-04-01&to=2027-04-30")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].mode").value("TIME"))
                .andExpect(jsonPath("$[0].blockCount").value(0))
                .andExpect(jsonPath("$[0].blocks").doesNotExist());
    }

    @Test
    void 상세_조회는_블록을_임베드하고_타인_범위는_404다() throws Exception {
        Long id = createScope("DATE", "2027-03-10", "2027-03-12");
        mvc.perform(put("/api/availability/scopes/" + id + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[{\"kind\":\"DATE\",\"blockDate\":\"2027-03-11\"}],"
                                + "\"expectedRevision\":0}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/availability/scopes/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DATE"))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.blocks.length()").value(1))
                .andExpect(jsonPath("$.blocks[0].kind").value("DATE"))
                .andExpect(jsonPath("$.blocks[0].blockDate").value("2027-03-11"));

        mvc.perform(get("/api/availability/scopes/" + id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AVAILABILITY-NOT-FOUND"));
    }

    @Test
    void 블록_PUT은_전량_교체하고_revision을_올린다() throws Exception {
        Long id = createScope("TIME", "2027-03-10", "2027-03-12");

        mvc.perform(put("/api/availability/scopes/" + id + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[{\"kind\":\"TIME\","
                                + "\"startAt\":\"2027-03-10T14:00:00\","
                                + "\"endAt\":\"2027-03-10T16:00:00\"},"
                                + "{\"kind\":\"TIME\",\"startAt\":\"2027-03-11T09:00:00\","
                                + "\"endAt\":\"2027-03-11T10:00:00\"}],"
                                + "\"expectedRevision\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.blocks.length()").value(2));

        // 교체 — 이전 2개가 사라지고 새 1개만 남는다
        mvc.perform(put("/api/availability/scopes/" + id + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[{\"kind\":\"TIME\","
                                + "\"startAt\":\"2027-03-12T20:00:00\","
                                + "\"endAt\":\"2027-03-12T21:00:00\"}],"
                                + "\"expectedRevision\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.blocks.length()").value(1))
                .andExpect(jsonPath("$.blocks[0].startAt").value("2027-03-12T20:00:00"));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM availability_blocks WHERE scope_id=?", Integer.class, id);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    void revision_불일치는_409고_expectedRevision_누락은_400이다() throws Exception {
        Long id = createScope("TIME", "2027-03-10", "2027-03-12");

        mvc.perform(put("/api/availability/scopes/" + id + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[],\"expectedRevision\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AVAILABILITY-REVISION-CONFLICT"));

        mvc.perform(put("/api/availability/scopes/" + id + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-VALIDATION"));
    }

    @Test
    void 블록_kind가_scope_mode와_다르면_400이다() throws Exception {
        Long id = createScope("TIME", "2027-03-10", "2027-03-12");

        mvc.perform(put("/api/availability/scopes/" + id + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[{\"kind\":\"DATE\",\"blockDate\":\"2027-03-11\"}],"
                                + "\"expectedRevision\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVAILABILITY-INVALID"));
    }

    @Test
    void 범위를_벗어난_블록과_시각_역전은_400이다() throws Exception {
        Long timeId = createScope("TIME", "2027-03-10", "2027-03-12");
        Long dateId = createScope("DATE", "2027-03-10", "2027-03-12");

        // TIME 블록이 범위 이탈 (rangeEnd+1일 이후 종료)
        mvc.perform(put("/api/availability/scopes/" + timeId + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[{\"kind\":\"TIME\","
                                + "\"startAt\":\"2027-03-12T23:00:00\","
                                + "\"endAt\":\"2027-03-13T01:00:00\"}],"
                                + "\"expectedRevision\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVAILABILITY-INVALID"));

        // TIME startAt >= endAt
        mvc.perform(put("/api/availability/scopes/" + timeId + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[{\"kind\":\"TIME\","
                                + "\"startAt\":\"2027-03-11T12:00:00\","
                                + "\"endAt\":\"2027-03-11T12:00:00\"}],"
                                + "\"expectedRevision\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVAILABILITY-INVALID"));

        // DATE blockDate 범위 밖
        mvc.perform(put("/api/availability/scopes/" + dateId + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[{\"kind\":\"DATE\",\"blockDate\":\"2027-03-13\"}],"
                                + "\"expectedRevision\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVAILABILITY-INVALID"));
    }

    @Test
    void COMPLETED_전이_후에도_같은_scope에_재PUT할_수_있다() throws Exception {
        Long id = createScope("TIME", "2027-03-10", "2027-03-12");

        mvc.perform(put("/api/availability/scopes/" + id + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[{\"kind\":\"TIME\","
                                + "\"startAt\":\"2027-03-10T14:00:00\","
                                + "\"endAt\":\"2027-03-10T16:00:00\"}],"
                                + "\"status\":\"COMPLETED\",\"expectedRevision\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.revision").value(1));

        // COMPLETED 상태에서 재수정 — 같은 scope PUT으로만 가능
        mvc.perform(put("/api/availability/scopes/" + id + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[],\"expectedRevision\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.blocks.length()").value(0));
    }

    @Test
    void 블록_0개의_COMPLETED는_허용된다() throws Exception {
        Long id = createScope("TIME", "2027-03-10", "2027-03-12");

        mvc.perform(put("/api/availability/scopes/" + id + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[],\"status\":\"COMPLETED\","
                                + "\"expectedRevision\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.blocks.length()").value(0));
    }

    @Test
    void 삭제는_204고_타인_범위_삭제는_404다() throws Exception {
        Long id = createScope("TIME", "2027-03-10", "2027-03-12");

        mvc.perform(delete("/api/availability/scopes/" + id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AVAILABILITY-NOT-FOUND"));

        mvc.perform(delete("/api/availability/scopes/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/availability/scopes/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void preferences는_행_없으면_기본_false고_PUT은_upsert다() throws Exception {
        mvc.perform(get("/api/availability/preferences")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.useCalendarInference").value(false));

        mvc.perform(put("/api/availability/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"useCalendarInference\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.useCalendarInference").value(true));

        mvc.perform(get("/api/availability/preferences")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.useCalendarInference").value(true));

        mvc.perform(put("/api/availability/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"useCalendarInference\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.useCalendarInference").value(false));
    }

    @Test
    void 타인_범위의_블록_PUT은_404다() throws Exception {
        Long id = createScope("TIME", "2027-03-10", "2027-03-12");

        mvc.perform(put("/api/availability/scopes/" + id + "/blocks")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[],\"expectedRevision\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AVAILABILITY-NOT-FOUND"));
    }

    @Test
    void COMPLETED_범위와_겹치는_신규_생성도_409다() throws Exception {
        Long id = createScope("TIME", "2027-03-10", "2027-03-12");
        mvc.perform(put("/api/availability/scopes/" + id + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[],\"status\":\"COMPLETED\","
                                + "\"expectedRevision\":0}"))
                .andExpect(status().isOk());

        // COMPLETED도 활성 범위 — 중첩 거부 대상 (§3.12 활성=INPUT|COMPLETED)
        mvc.perform(post("/api/availability/scopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIME\",\"rangeStart\":\"2027-03-11\","
                                + "\"rangeEnd\":\"2027-03-13\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AVAILABILITY-SCOPE-CONFLICT"));
    }

    @Test
    void status에_COMPLETED_외_값은_400이다() throws Exception {
        Long id = createScope("TIME", "2027-03-10", "2027-03-12");

        mvc.perform(put("/api/availability/scopes/" + id + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[],\"status\":\"INPUT\",\"expectedRevision\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-VALIDATION"));

        mvc.perform(put("/api/availability/scopes/" + id + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[],\"status\":\"CANCELLED\","
                                + "\"expectedRevision\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-VALIDATION"));
    }

    @Test
    void 블록_kind_enum_외_값은_400이다() throws Exception {
        Long id = createScope("TIME", "2027-03-10", "2027-03-12");

        mvc.perform(put("/api/availability/scopes/" + id + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[{\"kind\":\"WEEK\","
                                + "\"startAt\":\"2027-03-10T14:00:00\","
                                + "\"endAt\":\"2027-03-10T16:00:00\"}],"
                                + "\"expectedRevision\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-VALIDATION"));
    }

    @Test
    void 존재하지_않는_scope는_GET_PUT_DELETE_모두_404다() throws Exception {
        mvc.perform(get("/api/availability/scopes/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AVAILABILITY-NOT-FOUND"));

        mvc.perform(put("/api/availability/scopes/999999/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[],\"expectedRevision\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AVAILABILITY-NOT-FOUND"));

        mvc.perform(delete("/api/availability/scopes/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AVAILABILITY-NOT-FOUND"));
    }

    @Test
    void blocks_필드_생략은_400이다() throws Exception {
        Long id = createScope("TIME", "2027-03-10", "2027-03-12");

        // 생략은 전량 삭제 의도와 구분된다 — 의도된 삭제는 명시적 []로만
        mvc.perform(put("/api/availability/scopes/" + id + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-VALIDATION"));
    }

    @Test
    void blocks에_null_원소는_500이_아니라_400이다() throws Exception {
        Long id = createScope("TIME", "2027-03-10", "2027-03-12");

        mvc.perform(put("/api/availability/scopes/" + id + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[null],\"expectedRevision\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-VALIDATION"));
    }
}
