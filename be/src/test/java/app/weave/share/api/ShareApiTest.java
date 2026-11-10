package app.weave.share.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        jdbc.update("DELETE FROM shares");
        jdbc.update("DELETE FROM feed_subscriptions");
        jdbc.update("DELETE FROM friend_group_members");
        jdbc.update("DELETE FROM friend_groups");
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

    private MvcResult putShare(String token, String body) throws Exception {
        return mvc.perform(put("/api/shares")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private Long shareIdOf(MvcResult r) throws Exception {
        return com.jayway.jsonpath.JsonPath
                .<Number>read(r.getResponse().getContentAsString(), "$.shareId").longValue();
    }

    private String friendsBody() {
        return "{\"itemType\":\"DIARY\",\"itemId\":" + diaryId
                + ",\"visibility\":\"FRIENDS\"}";
    }

    @Test
    void upsert는_200과_ShareDetail을_반환하고_같은항목_재설정은_갱신이다() throws Exception {
        MvcResult r = putShare(meToken, friendsBody());

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        Long shareId = shareIdOf(r);
        mvc.perform(get("/api/shares/" + shareId)
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemType").value("DIARY"))
                .andExpect(jsonPath("$.visibility").value("FRIENDS"))
                .andExpect(jsonPath("$.item.date").value("2027-05-20"))
                .andExpect(jsonPath("$.item.title").value("제주 여행"))
                .andExpect(jsonPath("$.item.mood").value("happy"))
                .andExpect(jsonPath("$.owner.nickname").value("나"))
                .andExpect(jsonPath("$.replyCount").value(0));

        // 같은 항목 PRIVATE 재설정 — 같은 행, 충돌이 아니라 갱신
        MvcResult r2 = putShare(meToken,
                "{\"itemType\":\"DIARY\",\"itemId\":" + diaryId
                        + ",\"visibility\":\"PRIVATE\"}");
        assertThat(r2.getResponse().getStatus()).isEqualTo(200);
        assertThat(shareIdOf(r2)).isEqualTo(shareId);
    }

    @Test
    void upsert_에러는_계약_코드로_매핑된다() throws Exception {
        // 타인 항목 → 404 SHARE-ITEM-NOT-FOUND
        jdbc.update("INSERT INTO diaries(user_id,date,title,content) "
                + "VALUES (?,'2027-05-21','남의','본문')", f1Id);
        Long othersDiary = jdbc.queryForObject(
                "SELECT id FROM diaries WHERE user_id=?", Long.class, f1Id);
        mvc.perform(put("/api/shares")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemType\":\"DIARY\",\"itemId\":" + othersDiary
                                + ",\"visibility\":\"FRIENDS\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE-ITEM-NOT-FOUND"));

        // GROUP인데 groupId 누락 → 400 VISIBILITY-INVALID
        mvc.perform(put("/api/shares")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemType\":\"DIARY\",\"itemId\":" + diaryId
                                + ",\"visibility\":\"GROUP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VISIBILITY-INVALID"));

        // 잘못된 itemType·visibility → 400 (bean validation)
        mvc.perform(put("/api/shares")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemType\":\"TASK\",\"itemId\":1,"
                                + "\"visibility\":\"FRIENDS\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/shares")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemType\":\"DIARY\",\"itemId\":" + diaryId
                                + ",\"visibility\":\"EVERYONE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 피드와_상세는_범위내_친구에게만_보인다() throws Exception {
        Long shareId = shareIdOf(putShare(meToken, friendsBody()));

        mvc.perform(get("/api/shares/feed")
                        .header("Authorization", "Bearer " + f1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].shareId").value(shareId))
                .andExpect(jsonPath("$[0].itemTitle").value("제주 여행"))
                .andExpect(jsonPath("$[0].owner.nickname").value("나"));

        mvc.perform(get("/api/shares/" + shareId)
                        .header("Authorization", "Bearer " + f1Token))
                .andExpect(status().isOk());

        // 비친구는 피드 비어있고 상세 404
        mvc.perform(get("/api/shares/feed")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/shares/" + shareId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE-NOT-FOUND"));

        // limit 범위 초과는 400
        mvc.perform(get("/api/shares/feed?limit=51")
                        .header("Authorization", "Bearer " + f1Token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 친구별_목록은_비친구에게_404다() throws Exception {
        putShare(meToken, friendsBody());

        mvc.perform(get("/api/shares/by/" + meId)
                        .header("Authorization", "Bearer " + f1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].itemTitle").value("제주 여행"));

        mvc.perform(get("/api/shares/by/" + meId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE-NOT-FOUND"));
    }

    @Test
    void 내_공개설정_목록은_범위와_제목을_보여주고_PRIVATE는_제외한다() throws Exception {
        putShare(meToken, friendsBody());

        mvc.perform(get("/api/shares/mine")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].itemTitle").value("제주 여행"))
                .andExpect(jsonPath("$[0].visibility").value("FRIENDS"));

        putShare(meToken, "{\"itemType\":\"DIARY\",\"itemId\":" + diaryId
                + ",\"visibility\":\"PRIVATE\"}");
        mvc.perform(get("/api/shares/mine")
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 답글_작성은_201과_ReplyNode를_반환하고_스레드를_조회한다() throws Exception {
        Long shareId = shareIdOf(putShare(meToken, friendsBody()));

        MvcResult r = mvc.perform(post("/api/shares/" + shareId + "/replies")
                        .header("Authorization", "Bearer " + f1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"잘 봤어\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.id").value(f1Id))
                .andExpect(jsonPath("$.author.nickname").value("일"))
                .andExpect(jsonPath("$.content").value("잘 봤어"))
                .andExpect(jsonPath("$.deleted").value(false))
                .andExpect(jsonPath("$.children").isArray())
                .andReturn();
        Long replyId = com.jayway.jsonpath.JsonPath
                .<Number>read(r.getResponse().getContentAsString(), "$.id").longValue();

        mvc.perform(post("/api/shares/" + shareId + "/replies")
                        .header("Authorization", "Bearer " + meToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"고마워\",\"parentId\":" + replyId + "}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/shares/" + shareId + "/replies")
                        .header("Authorization", "Bearer " + f1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].children.length()").value(1))
                .andExpect(jsonPath("$[0].children[0].content").value("고마워"));

        mvc.perform(get("/api/shares/" + shareId)
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(jsonPath("$.replyCount").value(2));
    }

    @Test
    void 답글_에러는_계약_코드로_매핑된다() throws Exception {
        Long shareId = shareIdOf(putShare(meToken, friendsBody()));

        // 비열람자 작성·조회 → 404 SHARE-NOT-FOUND
        mvc.perform(post("/api/shares/" + shareId + "/replies")
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE-NOT-FOUND"));
        mvc.perform(get("/api/shares/" + shareId + "/replies")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());

        // 공백·초과 → 400 REPLY-INVALID
        mvc.perform(post("/api/shares/" + shareId + "/replies")
                        .header("Authorization", "Bearer " + f1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPLY-INVALID"));

        // 없는 부모 → 400 REPLY-PARENT-INVALID
        mvc.perform(post("/api/shares/" + shareId + "/replies")
                        .header("Authorization", "Bearer " + f1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"x\",\"parentId\":999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPLY-PARENT-INVALID"));

        // limit 범위 초과 → 400
        mvc.perform(get("/api/shares/" + shareId + "/replies?limit=51")
                        .header("Authorization", "Bearer " + f1Token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 답글_수정과_삭제는_작성자_또는_소유자만_가능하다() throws Exception {
        Long shareId = shareIdOf(putShare(meToken, friendsBody()));
        String f2Token = jwtProvider.createAccessToken(f2Id, List.of("ROLE_USER"));
        MvcResult r = mvc.perform(post("/api/shares/" + shareId + "/replies")
                        .header("Authorization", "Bearer " + f1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"원문\"}"))
                .andReturn();
        Long replyId = com.jayway.jsonpath.JsonPath
                .<Number>read(r.getResponse().getContentAsString(), "$.id").longValue();

        // 작성자 아닌 열람자 수정 → 404 REPLY-NOT-AUTHOR
        mvc.perform(patch("/api/shares/" + shareId + "/replies/" + replyId)
                        .header("Authorization", "Bearer " + f2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"남의 글 수정\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPLY-NOT-AUTHOR"));

        // 작성자 수정 → 200
        mvc.perform(patch("/api/shares/" + shareId + "/replies/" + replyId)
                        .header("Authorization", "Bearer " + f1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"고침\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("고침"));

        // 다른 share 경로의 rid → 404 REPLY-NOT-FOUND
        mvc.perform(patch("/api/shares/" + shareId + "/replies/999999")
                        .header("Authorization", "Bearer " + f1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPLY-NOT-FOUND"));

        // 소유자 삭제 → 204, 이후 목록에서 tombstone 마스킹
        mvc.perform(delete("/api/shares/" + shareId + "/replies/" + replyId)
                        .header("Authorization", "Bearer " + meToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/shares/" + shareId + "/replies")
                        .header("Authorization", "Bearer " + f1Token))
                .andExpect(jsonPath("$[0].deleted").value(true))
                .andExpect(jsonPath("$[0].content").value(""));
    }

    @Test
    void 사진_엔드포인트는_비열람자에게_404다() throws Exception {
        Long shareId = shareIdOf(putShare(meToken, friendsBody()));

        mvc.perform(get("/api/shares/" + shareId + "/photos/1")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE-NOT-FOUND"));
        // 열람자지만 사진 미존재 → SHARE-ITEM-NOT-FOUND
        mvc.perform(get("/api/shares/" + shareId + "/photos/999999")
                        .header("Authorization", "Bearer " + f1Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE-ITEM-NOT-FOUND"));
    }
}
