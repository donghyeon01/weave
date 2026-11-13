package app.weave.chat.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.weave.auth.jwt.JwtProvider;
import app.weave.chat.domain.ChatRoom;
import app.weave.chat.domain.Mode;
import app.weave.chat.service.ChatService;
import app.weave.chat.service.ChatService.ChatMessageView;
import java.time.LocalDateTime;
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
class ChatControllerTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JwtProvider jwtProvider;
    @Autowired
    ChatService chatService;

    private Long meId;
    private Long p1Id;
    private Long outId;
    private String token;
    private String outToken;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM chat_messages");
        jdbc.update("DELETE FROM chat_room_members");
        jdbc.update("DELETE FROM chat_rooms");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES "
                + "('me@weave.local','나','나','demo'),('p1@weave.local','일','일','demo'),"
                + "('out@weave.local','외','외','demo')");
        meId = idOf("me@weave.local");
        p1Id = idOf("p1@weave.local");
        outId = idOf("out@weave.local");
        token = jwtProvider.createAccessToken(meId, List.of("ROLE_USER"));
        outToken = jwtProvider.createAccessToken(outId, List.of("ROLE_USER"));
    }

    private Long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private ChatRoom timeRoom(long proposalId, List<Long> members) {
        return chatService.createRoom(proposalId, "방" + proposalId, Mode.TIME,
                LocalDateTime.of(2026, 12, 1, 10, 30), LocalDateTime.of(2026, 12, 1, 12, 0),
                members, 1);
    }

    @Test
    void rooms는_멤버의_모든방을_status와_slot_unread로_반환한다() throws Exception {
        ChatRoom time = timeRoom(1L, List.of(meId, p1Id));
        // DATE 방 — 저장 end는 배타 상한(+1일 자정)이라 응답은 -1일
        chatService.createRoom(2L, "날짜방", Mode.DATE, LocalDateTime.of(2026, 12, 5, 0, 0),
                LocalDateTime.of(2026, 12, 8, 0, 0), List.of(meId, p1Id), 3);
        // CLOSED 방도 status=CLOSED로 노출 (읽기 전용)
        chatService.closeRoom(timeRoom(3L, List.of(meId)).getProposalId());
        chatService.send(p1Id, time.getId(), "unread", null);

        mvc.perform(get("/api/chat/rooms").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.proposalId==1)].slot.type").value("TIME"))
                .andExpect(jsonPath("$[?(@.proposalId==1)].slot.date").value("2026-12-01"))
                .andExpect(jsonPath("$[?(@.proposalId==1)].slot.startTime").value("10:30"))
                .andExpect(jsonPath("$[?(@.proposalId==1)].slot.endTime").value("12:00"))
                .andExpect(jsonPath("$[?(@.proposalId==1)].unreadCount").value(1))
                .andExpect(jsonPath("$[?(@.proposalId==1)].status").value("OPEN"))
                .andExpect(jsonPath("$[?(@.proposalId==1)].lastMessage").value("unread"))
                .andExpect(jsonPath("$[?(@.proposalId==1)].lastMessageAt").isNotEmpty())
                .andExpect(jsonPath("$[?(@.proposalId==1)].memberCount").value(2))
                .andExpect(jsonPath("$[?(@.proposalId==1)].occurrenceCount").value(1))
                .andExpect(jsonPath("$[?(@.proposalId==2)].occurrenceCount").value(3))
                .andExpect(jsonPath("$[?(@.proposalId==2)].lastMessage")
                        .value(hasItem(nullValue())))
                .andExpect(jsonPath("$[?(@.proposalId==2)].memberCount").value(2))
                .andExpect(jsonPath("$[?(@.proposalId==2)].slot.type").value("DATE"))
                .andExpect(jsonPath("$[?(@.proposalId==2)].slot.startDate").value("2026-12-05"))
                .andExpect(jsonPath("$[?(@.proposalId==2)].slot.endDate").value("2026-12-07"))
                .andExpect(jsonPath("$[?(@.proposalId==2)].status").value("OPEN"))
                .andExpect(jsonPath("$[?(@.proposalId==3)].status").value("CLOSED"));

        // 비멤버는 빈 목록
        mvc.perform(get("/api/chat/rooms").header("Authorization", "Bearer " + outToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void messages는_커서와_limit으로_오름차순_페이지를_반환한다() throws Exception {
        ChatRoom room = timeRoom(10L, List.of(meId, p1Id));
        for (int i = 1; i <= 4; i++) {
            chatService.send(p1Id, room.getId(), "m" + i, null);
        }

        var res = mvc.perform(get("/api/chat/rooms/{id}/messages", room.getId())
                        .param("limit", "2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("m3"))
                .andExpect(jsonPath("$[0].nickname").value("일"))
                .andExpect(jsonPath("$[1].content").value("m4"))
                .andReturn();
        Integer cursor = com.jayway.jsonpath.JsonPath
                .read(res.getResponse().getContentAsString(), "$[0].id");

        mvc.perform(get("/api/chat/rooms/{id}/messages", room.getId())
                        .param("cursor", String.valueOf(cursor))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("m1"))
                .andExpect(jsonPath("$[1].content").value("m2"));

        // limit 범위 초과는 COMMON-VALIDATION
        mvc.perform(get("/api/chat/rooms/{id}/messages", room.getId())
                        .param("limit", "101").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-VALIDATION"));

        // 숫자 아닌 커서는 타입 불일치 — 500이 아니라 400 COMMON-MALFORMED
        mvc.perform(get("/api/chat/rooms/{id}/messages", room.getId())
                        .param("cursor", "abc").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-MALFORMED"));
    }

    @Test
    void 비멤버의_이력과_읽음은_404_NOT_MEMBER() throws Exception {
        ChatRoom room = timeRoom(20L, List.of(meId));
        ChatMessageView m = chatService.send(meId, room.getId(), "hi", null);

        mvc.perform(get("/api/chat/rooms/{id}/messages", room.getId())
                        .header("Authorization", "Bearer " + outToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT-NOT-MEMBER"));
        mvc.perform(post("/api/chat/rooms/{id}/read", room.getId())
                        .header("Authorization", "Bearer " + outToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadMessageId\":" + m.id() + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT-NOT-MEMBER"));

        // 미존재 방은 ROOM-NOT-FOUND
        mvc.perform(get("/api/chat/rooms/{id}/messages", 999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT-ROOM-NOT-FOUND"));
    }

    @Test
    void CLOSED_방도_멤버는_이력과_읽음_갱신이_가능하다() throws Exception {
        ChatRoom room = timeRoom(40L, List.of(meId, p1Id));
        ChatMessageView m = chatService.send(p1Id, room.getId(), "hi", null);
        chatService.closeRoom(40L);

        mvc.perform(get("/api/chat/rooms/{id}/messages", room.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("hi"));

        mvc.perform(post("/api/chat/rooms/{id}/read", room.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadMessageId\":" + m.id() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastReadMessageId").value(m.id()));
    }

    @Test
    void read는_커서를_갱신하고_타방_메시지는_400() throws Exception {
        ChatRoom room = timeRoom(30L, List.of(meId, p1Id));
        ChatRoom other = timeRoom(31L, List.of(meId, p1Id));
        ChatMessageView m = chatService.send(p1Id, room.getId(), "hi", null);
        ChatMessageView foreign = chatService.send(p1Id, other.getId(), "x", null);

        mvc.perform(post("/api/chat/rooms/{id}/read", room.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadMessageId\":" + m.id() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastReadMessageId").value(m.id()));

        mvc.perform(post("/api/chat/rooms/{id}/read", room.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadMessageId\":" + foreign.id() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHAT-MESSAGE-INVALID"));
    }
}
