package app.weave.chat.api;

import static org.assertj.core.api.Assertions.assertThat;

import app.weave.auth.jwt.JwtProvider;
import app.weave.chat.domain.ChatRoom;
import app.weave.chat.domain.Mode;
import app.weave.chat.service.ChatService;
import app.weave.chat.service.ChatService.ChatMessageView;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * api-contract §3.10 STOMP 통합 — 실제 포트 바인딩으로 /app 송신·/topic 수신·
 * 멤버십 인터셉터의 ERROR 프레임을 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ChatStompIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JwtProvider jwtProvider;
    @Autowired
    ChatService chatService;
    @Autowired
    SimpMessagingTemplate messagingTemplate;
    @Autowired
    org.springframework.messaging.simp.user.SimpUserRegistry userRegistry;

    private WebSocketStompClient stompClient;
    private Long meId;
    private Long p1Id;
    private Long outId;

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
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    private Long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private String token(Long userId) {
        return jwtProvider.createAccessToken(userId, List.of("ROLE_USER"));
    }

    private ChatRoom room(long proposalId, List<Long> members) {
        return chatService.createRoom(proposalId, "방" + proposalId, Mode.TIME,
                LocalDateTime.of(2026, 12, 1, 10, 0), LocalDateTime.of(2026, 12, 1, 12, 0),
                members);
    }

    /** ERROR 프레임의 message 헤더({"code":"..."})를 큐로 수집하는 세션 핸들러. */
    private static class ErrorCapture extends StompSessionHandlerAdapter {
        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        int connectedCount;

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            connectedCount++;
        }

        // ERROR 프레임은 handleException이 아니라 handleFrame으로 라우팅된다.
        // 중복 CONNECTED 프레임이 오면 이미 연결된 세션의 handleFrame으로 들어온다
        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            if (StompCommand.CONNECTED.name().equals(headers.getFirst("stompCommand"))) {
                connectedCount++;
                return;
            }
            errors.add(String.valueOf(headers.getFirst("message")));
        }

        @Override
        public void handleException(StompSession session, StompCommand command,
                StompHeaders headers, byte[] payload, Throwable exception) {
            errors.add(headers.getFirst("message") != null ? headers.getFirst("message")
                    : exception != null ? exception.getMessage() : "");
        }

        String nextError() throws InterruptedException {
            return errors.poll(10, TimeUnit.SECONDS);
        }
    }

    private static class BroadcastCapture implements StompFrameHandler {
        final BlockingQueue<Map<String, Object>> messages = new LinkedBlockingQueue<>();

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            messages.add((Map<String, Object>) payload);
        }

        Map<String, Object> next() throws InterruptedException {
            return messages.poll(10, TimeUnit.SECONDS);
        }
    }

    private record Conn(StompSession session, ErrorCapture errors) {
    }

    private Conn connect(Long userId) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token(userId));
        ErrorCapture handler = new ErrorCapture();
        StompSession session = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), headers, handler)
                .get(10, TimeUnit.SECONDS);
        return new Conn(session, handler);
    }

    @Test
    void 멤버_SEND는_구독자에게_이력동형으로_브로드캐스트되고_이력에_반영된다() throws Exception {
        ChatRoom r = room(1L, List.of(meId, p1Id));
        Conn me = connect(meId);
        Conn p1 = connect(p1Id);
        BroadcastCapture capture = new BroadcastCapture();
        p1.session().subscribe("/topic/rooms/" + r.getId(), capture);
        Thread.sleep(300); // 구독 등록 레이스 방지

        me.session().send("/app/rooms/" + r.getId(),
                Map.of("content", "안녕", "clientMessageId", "c-1"));

        Map<String, Object> received = capture.next();
        assertThat(received).isNotNull();
        assertThat(received.get("content")).isEqualTo("안녕");
        assertThat(((Number) received.get("senderId")).longValue()).isEqualTo(meId);
        assertThat(received.get("nickname")).isEqualTo("나");

        // REST 이력에 반영 — STOMP 브로드캐스트와 동형
        ChatMessageView view = chatService.history(meId, r.getId(), null, null).get(0);
        assertThat(view.content()).isEqualTo("안녕");
        assertThat(((Number) received.get("id")).longValue()).isEqualTo(view.id());
        assertThat(me.errors().nextError()).isNull();
    }

    @Test
    void 비멤버의_SEND와_SUBSCRIBE는_ERROR_프레임으로_거부된다() throws Exception {
        ChatRoom r = room(2L, List.of(meId, p1Id));
        Conn outsider = connect(outId);

        outsider.session().send("/app/rooms/" + r.getId(), Map.of("content", "hi"));
        String sendError = outsider.errors().nextError();
        assertThat(sendError).contains("CHAT-NOT-MEMBER");

        // Spring은 ERROR 프레임 송신 후 세션을 닫는다 — 거부마다 새 연결로 검증
        Conn outsider2 = connect(outId);
        outsider2.session().subscribe("/topic/rooms/" + r.getId(),
                new BroadcastCapture());
        String subError = outsider2.errors().nextError();
        assertThat(subError).contains("CHAT-NOT-MEMBER");

        // 멤버라도 미존재 방은 ROOM-NOT-FOUND
        Conn me = connect(meId);
        me.session().send("/app/rooms/999999", Map.of("content", "hi"));
        assertThat(me.errors().nextError()).contains("CHAT-ROOM-NOT-FOUND");
    }

    @Test
    void CLOSED_방의_멤버_SEND는_CHAT_ROOM_CLOSED() throws Exception {
        ChatRoom r = room(3L, List.of(meId));
        chatService.closeRoom(3L);
        Conn me = connect(meId);

        me.session().send("/app/rooms/" + r.getId(), Map.of("content", "hi"));
        assertThat(me.errors().nextError()).contains("CHAT-ROOM-CLOSED");
    }

    @Test
    void 와일드카드_SUBSCRIBE는_ERROR로_거부된다() throws Exception {
        Conn me = connect(meId);
        me.session().subscribe("/topic/rooms/*", new BroadcastCapture());
        assertThat(me.errors().nextError()).contains("CHAT-ROOM-NOT-FOUND");

        // ERROR 송신 후 세션 종료 — 새 연결로 패턴 목적지 구독도 차단 확인
        Conn me2 = connect(meId);
        me2.session().subscribe("/topic/**", new BroadcastCapture());
        assertThat(me2.errors().nextError()).contains("CHAT-ROOM-NOT-FOUND");
    }

    @Test
    void topic_직행_SEND는_ERROR이고_구독자_수신과_DB_저장이_없다() throws Exception {
        ChatRoom r = room(5L, List.of(meId, p1Id));
        Conn me = connect(meId);
        Conn p1 = connect(p1Id);
        BroadcastCapture capture = new BroadcastCapture();
        p1.session().subscribe("/topic/rooms/" + r.getId(), capture);
        Thread.sleep(300);

        // /app 경유가 아닌 브로커 직행 SEND — 멤버라도 차단해야 한다
        me.session().send("/topic/rooms/" + r.getId(),
                Map.of("content", "위조", "senderId", 0));

        assertThat(me.errors().nextError()).contains("CHAT-ROOM-NOT-FOUND");
        assertThat(capture.messages.poll(2, TimeUnit.SECONDS)).isNull();
        assertThat(chatService.history(meId, r.getId(), null, null)).isEmpty();
    }

    @Test
    void 유효하지_않은_payload의_SEND는_CHAT_MESSAGE_INVALID_ERROR() throws Exception {
        ChatRoom r = room(6L, List.of(meId));

        Conn c1 = connect(meId);
        c1.session().send("/app/rooms/" + r.getId(), Map.of("content", "   "));
        assertThat(c1.errors().nextError()).contains("CHAT-MESSAGE-INVALID");

        Conn c2 = connect(meId);
        c2.session().send("/app/rooms/" + r.getId(),
                Map.of("content", "x".repeat(2001)));
        assertThat(c2.errors().nextError()).contains("CHAT-MESSAGE-INVALID");

        Conn c3 = connect(meId);
        c3.session().send("/app/rooms/" + r.getId(),
                Map.of("content", "ok", "clientMessageId", "k".repeat(65)));
        assertThat(c3.errors().nextError()).contains("CHAT-MESSAGE-INVALID");

        assertThat(chatService.history(meId, r.getId(), null, null)).isEmpty();
    }

    @Test
    void CONNECTED는_1회이고_notification과_room구독이_공존한다() throws Exception {
        ChatRoom r = room(7L, List.of(meId, p1Id));
        Conn me = connect(meId);
        Conn p1 = connect(p1Id);
        assertThat(me.errors().connectedCount).isEqualTo(1);
        assertThat(p1.errors().connectedCount).isEqualTo(1);

        // notification 구독 경로 회귀 — /queue,/user 브로커 목적지도 같은 브로커에서 동작
        BroadcastCapture notif = new BroadcastCapture();
        me.session().subscribe("/user/queue/notifications", notif);
        BroadcastCapture roomCap = new BroadcastCapture();
        p1.session().subscribe("/topic/rooms/" + r.getId(), roomCap);
        Thread.sleep(300);

        // 구독 등록은 비동기 — 짧은 윈도에 유실될 수 있어 수신될 때까지 재전송한다
        var u = userRegistry.getUser(String.valueOf(meId));
        Map<String, Object> got = null;
        for (int i = 0; i < 10 && got == null; i++) {
            messagingTemplate.convertAndSendToUser(String.valueOf(meId),
                    "/queue/notifications", Map.of("type", "TEST"));
            got = notif.messages.poll(500, TimeUnit.MILLISECONDS);
        }
        assertThat(u).isNotNull();
        var subs = u.getSessions().stream()
                .flatMap(s -> s.getSubscriptions().stream())
                .map(org.springframework.messaging.simp.user.SimpSubscription::getDestination)
                .toList();
        assertThat(got).as("subs=%s", subs).isNotNull();

        me.session().send("/app/rooms/" + r.getId(), Map.of("content", "공존"));
        assertThat(roomCap.next()).isNotNull();
    }

    @Test
    void clientMessageId_재전송은_같은_행을_반환하고_이력은_중복되지_않는다() throws Exception {
        ChatRoom r = room(4L, List.of(meId, p1Id));
        Conn me = connect(meId);
        Conn p1 = connect(p1Id);
        BroadcastCapture capture = new BroadcastCapture();
        p1.session().subscribe("/topic/rooms/" + r.getId(), capture);
        Thread.sleep(300);

        var payload = Map.of("content", "멱등", "clientMessageId", "dup-1");
        me.session().send("/app/rooms/" + r.getId(), payload);
        me.session().send("/app/rooms/" + r.getId(), payload);

        Map<String, Object> first = capture.next();
        Map<String, Object> second = capture.next();
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        // 재전송은 같은 행을 재브로드캐스트 — id 동일, 이력에는 1건만
        assertThat(second.get("id")).isEqualTo(first.get("id"));
        assertThat(chatService.history(meId, r.getId(), null, null)).hasSize(1);
    }
}
