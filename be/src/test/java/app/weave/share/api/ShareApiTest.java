package app.weave.share.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ShareApiTest {

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
    private Long f2Id;
    private Long outsiderId;
    private String meToken;
    private String f1Token;
    private String outsiderToken;
    private Long diaryId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM share_replies");
        jdbc.update("DELETE FROM share_targets");
        jdbc.update("DELETE FROM shares");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM diary_photos");
        jdbc.update("DELETE FROM diaries");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM friendships");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES "
                + "('me@weave.local','나','나','demo'),('f1@weave.local','일','일','demo'),"
                + "('f2@weave.local','이','이','demo'),('out@weave.local','외','외','demo')");
        meId = idOf("me@weave.local");
        f1Id = idOf("f1@weave.local");
        f2Id = idOf("f2@weave.local");
        outsiderId = idOf("out@weave.local");
        meToken = jwtProvider.createAccessToken(meId, List.of("ROLE_USER"));
        f1Token = jwtProvider.createAccessToken(f1Id, List.of("ROLE_USER"));
        outsiderToken = jwtProvider.createAccessToken(outsiderId, List.of("ROLE_USER"));
        jdbc.update("INSERT INTO friendships(user_id,friend_id,status) "
                + "VALUES (?,?,'ACCEPTED'),(?,?,'ACCEPTED')", meId, f1Id, meId, f2Id);
        jdbc.update("INSERT INTO diaries(user_id,date,title,content,mood) "
                + "VALUES (?,'2027-05-20','제주 여행','본문','happy')", meId);
        diaryId = jdbc.queryForObject("SELECT id FROM diaries LIMIT 1", Long.class);
    }

    private Long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private MvcResult createShare(String token, String body) throws Exception {
        return mvc.perform(post("/api/shares")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private Long shareIdOf(MvcResult r) throws Exception {
        return com.jayway.jsonpath.JsonPath
                .<Number>read(r.getResponse().getContentAsString(), "$.shareId").longValue();
    }

    @Test
    void 공유_생성은_201과_ShareDetail을_반환한다() throws Exception {
        MvcResult r = createShare(meToken,
                "{\"itemType\":\"DIARY\",\"itemId\":" + diaryId
                        + ",\"targetUserIds\":[" + f1Id + "]}");

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        assertThat(r.getResponse().getHeader("Location")).startsWith("/api/shares/");
        Long shareId = shareIdOf(r);
        mvc.perform(get("/api/shares/" + shareId)
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemType").value("DIARY"))
                .andExpect(jsonPath("$.item.date").value("2027-05-20"))
                .andExpect(jsonPath("$.item.title").value("제주 여행"))
                .andExpect(jsonPath("$.item.mood").value("happy"))
                .andExpect(jsonPath("$.owner.nickname").value("나"))
                .andExpect(jsonPath("$.targetUserIds[0]").value(f1Id))
                .andExpect(jsonPath("$.replyCount").value(0));
    }

    @Test
    void 생성_에러는_계약_코드로_매핑된다() throws Exception {
        // 비친구 대상 → 400 SHARE-TARGET-INVALID
        createShare(meToken, "{\"itemType\":\"DIARY\",\"itemId\":" + diaryId
                        + ",\"targetUserIds\":[" + outsiderId + "]}")
                .getResponse().getStatus();
        mvc.perform(post("/api/shares")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemType\":\"DIARY\",\"itemId\":" + diaryId
                                + ",\"targetUserIds\":[" + outsiderId + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SHARE-TARGET-INVALID"));

        // 타인 항목 → 404 SHARE-ITEM-NOT-FOUND
        jdbc.update("INSERT INTO diaries(user_id,date,title,content) "
                + "VALUES (?,'2027-05-21','남의','본문')", f1Id);
        Long othersDiary = jdbc.queryForObject(
                "SELECT id FROM diaries WHERE user_id=?", Long.class, f1Id);
        mvc.perform(post("/api/shares")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemType\":\"DIARY\",\"itemId\":" + othersDiary
                                + ",\"targetUserIds\":[" + f1Id + "]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE-ITEM-NOT-FOUND"));

        // 잘못된 itemType → 400
        mvc.perform(post("/api/shares")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemType\":\"TASK\",\"itemId\":1,\"targetUserIds\":["
                                + f1Id + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 중복_공유는_409다() throws Exception {
        String body = "{\"itemType\":\"DIARY\",\"itemId\":" + diaryId
                + ",\"targetUserIds\":[" + f1Id + "]}";
        assertThat(createShare(meToken, body).getResponse().getStatus()).isEqualTo(201);

        mvc.perform(post("/api/shares")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHARE-ALREADY-EXISTS"));
    }

    @Test
    void 받은_피드와_상세는_대상에게만_보인다() throws Exception {
        Long shareId = shareIdOf(createShare(meToken,
                "{\"itemType\":\"DIARY\",\"itemId\":" + diaryId
                        + ",\"targetUserIds\":[" + f1Id + "]}"));

        mvc.perform(get("/api/shares/received")
                        .header("Authorization", "Bearer " + f1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].shareId").value(shareId))
                .andExpect(jsonPath("$[0].itemTitle").value("제주 여행"))
                .andExpect(jsonPath("$[0].owner.nickname").value("나"));

        // 대상 상세 — targetUserIds는 소유자에게만 노출
        mvc.perform(get("/api/shares/" + shareId)
                        .header("Authorization", "Bearer " + f1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetUserIds").doesNotExist());

        // 비대상은 피드 비어있고 상세 404
        mvc.perform(get("/api/shares/received")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/shares/" + shareId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE-NOT-AUDIENCE"));

        // limit 범위 초과는 400
        mvc.perform(get("/api/shares/received?limit=51")
                        .header("Authorization", "Bearer " + f1Token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 대상편집과_해제는_소유자만_가능하다() throws Exception {
        Long shareId = shareIdOf(createShare(meToken,
                "{\"itemType\":\"DIARY\",\"itemId\":" + diaryId
                        + ",\"targetUserIds\":[" + f1Id + "]}"));

        // add → 200 + targetUserIds 갱신
        mvc.perform(patch("/api/shares/" + shareId + "/targets")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"add\":[" + f2Id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetUserIds.length()").value(2));

        // 비소유자 편집·삭제는 404
        mvc.perform(patch("/api/shares/" + shareId + "/targets")
                        .header("Authorization", "Bearer " + f1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"add\":[" + f2Id + "]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE-NOT-FOUND"));
        mvc.perform(delete("/api/shares/" + shareId)
                        .header("Authorization", "Bearer " + f1Token))
                .andExpect(status().isNotFound());

        // 소유자 삭제 → 204, 이후 전원 404
        mvc.perform(delete("/api/shares/" + shareId)
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/shares/" + shareId)
                        .header("Authorization", "Bearer " + f1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void 내_공유_목록은_대상과_제목을_보여준다() throws Exception {
        createShare(meToken, "{\"itemType\":\"DIARY\",\"itemId\":" + diaryId
                + ",\"targetUserIds\":[" + f1Id + "," + f2Id + "]}");

        mvc.perform(get("/api/shares")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].itemTitle").value("제주 여행"))
                .andExpect(jsonPath("$[0].targetUserIds.length()").value(2));
    }

    @Test
    void 사진_엔드포인트는_비대상에게_404다() throws Exception {
        Long shareId = shareIdOf(createShare(meToken,
                "{\"itemType\":\"DIARY\",\"itemId\":" + diaryId
                        + ",\"targetUserIds\":[" + f1Id + "]}"));

        mvc.perform(get("/api/shares/" + shareId + "/photos/1")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE-NOT-AUDIENCE"));
        // 대상이지만 사진 미존재 → SHARE-ITEM-NOT-FOUND
        mvc.perform(get("/api/shares/" + shareId + "/photos/999999")
                        .header("Authorization", "Bearer " + f1Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE-ITEM-NOT-FOUND"));
    }
}
