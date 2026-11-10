package app.weave.chat.service;

import app.weave.chat.domain.ChatRoom;
import app.weave.chat.domain.ChatRoom.Status;
import app.weave.chat.domain.ChatRoomRepository;
import app.weave.common.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * chat 목적지의 멤버십·방 상태·payload 검증 (api-contract §3.10) — deny-by-default.
 * SEND는 ^/app/rooms/{id}$만 허용하고 그 외 목적지(/topic/** 직행 포함)는 전부 거부 —
 * SimpleBroker 직행으로 멤버십·CLOSED·DB 저장을 우회하는 위조 송신 차단.
 * SUBSCRIBE는 /topic 목적지가 ^/topic/rooms/{id}$ 정확 매칭일 때만 통과 —
 * /topic/*·/topic/** 패턴 구독으로 모든 방 수신하는 우회 차단.
 * /topic 아닌 구독(/user/queue/notifications 등)과 그 외 프레임은 통과.
 * 실패는 MessagingException → STOMP ERROR 프레임(message 헤더에 {"code":"...","clientMessageId":"..."} JSON — clientMessageId는 SEND 거부 시만).
 */
@Component
public class StompRoomAuthInterceptor implements ChannelInterceptor {

    private static final Pattern SEND_ROOM = Pattern.compile("^/app/rooms/(\\d+)$");
    private static final Pattern SUBSCRIBE_ROOM = Pattern.compile("^/topic/rooms/(\\d+)$");
    private static final int MAX_CONTENT = 2000;
    private static final int MAX_CLIENT_MESSAGE_ID = 64;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatService chatService;
    private final ChatRoomRepository roomRepository;

    public StompRoomAuthInterceptor(ChatService chatService,
            ChatRoomRepository roomRepository) {
        this.chatService = chatService;
        this.roomRepository = roomRepository;
    }

    /**
     * ERROR 프레임 본문 규약 — 기본 StompSubProtocolErrorHandler는 예외 메시지를
     * ERROR 프레임의 message 헤더에 실으므로 {"code":"..."} JSON을 메시지로 던진다.
     * ChatMessageHandler의 BusinessException 변환에도 재사용한다.
     */
    public static MessagingException error(ErrorCode code) {
        return error(code, null);
    }

    /**
     * clientMessageId를 봉투에 싣는 오버로드 — fe가 어느 송신이 거부됐는지 상관한다 (이슈 #18).
     * 클라이언트 키는 ObjectMapper로 직렬화해 봉투 JSON 인젝션을 차단한다.
     */
    public static MessagingException error(ErrorCode code, @Nullable String clientMessageId) {
        try {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("code", code.getCode());
            if (clientMessageId != null) {
                node.put("clientMessageId", clientMessageId);
            }
            return new MessagingException(OBJECT_MAPPER.writeValueAsString(node));
        } catch (JsonProcessingException e) {
            return new MessagingException("{\"code\":\"" + code.getCode() + "\"}");
        }
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getDestination() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.SEND) {
            return checkSend(message, accessor);
        }
        if (command == StompCommand.SUBSCRIBE) {
            checkSubscribe(accessor);
        }
        return message;
    }

    private Message<?> checkSend(Message<?> message, StompHeaderAccessor accessor) {
        String clientMessageId = extractClientMessageId(message.getPayload());
        Matcher matcher = SEND_ROOM.matcher(accessor.getDestination());
        if (!matcher.matches()) {
            // /app prefix 외 모든 SEND 차단 — 허용 패턴이 아니면 목적지 구분 없이 거부
            throw error(ErrorCode.CHAT_ROOM_NOT_FOUND, clientMessageId);
        }
        requireOpenMember(accessor, Long.valueOf(matcher.group(1)), true, clientMessageId);
        validatePayload(message.getPayload(), clientMessageId);
        return message;
    }

    private void checkSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (!destination.startsWith("/topic")) {
            return; // /user/queue/notifications 등 notification 구독은 통과
        }
        Matcher matcher = SUBSCRIBE_ROOM.matcher(destination);
        if (!matcher.matches()) {
            throw error(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }
        requireOpenMember(accessor, Long.valueOf(matcher.group(1)), false, null);
    }

    /** SEND는 CLOSED까지 검사, SUBSCRIBE는 멤버십까지만 (종료 통지 수신 유지). */
    private void requireOpenMember(StompHeaderAccessor accessor, Long roomId, boolean send,
            @Nullable String clientMessageId) {
        ChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> error(ErrorCode.CHAT_ROOM_NOT_FOUND, clientMessageId));
        Long me = userId(accessor.getUser());
        if (me == null || !chatService.isMember(roomId, me)) {
            throw error(ErrorCode.CHAT_NOT_MEMBER, clientMessageId);
        }
        if (send && room.getStatus() == Status.CLOSED) {
            throw error(ErrorCode.CHAT_ROOM_CLOSED, clientMessageId);
        }
    }

    /**
     * {content, clientMessageId} 선검증 — 핸들러 도달 전에 ERROR 프레임 경로를 태운다.
     * 서비스의 검증은 defense in depth로 유지한다.
     */
    private void validatePayload(Object payload, @Nullable String clientMessageId) {
        JsonNode body;
        try {
            body = payload instanceof byte[] bytes ? OBJECT_MAPPER.readTree(bytes) : null;
        } catch (Exception e) {
            throw error(ErrorCode.CHAT_MESSAGE_INVALID, clientMessageId);
        }
        if (body == null) {
            throw error(ErrorCode.CHAT_MESSAGE_INVALID, clientMessageId);
        }
        JsonNode content = body.get("content");
        if (content == null || !content.isTextual()) {
            throw error(ErrorCode.CHAT_MESSAGE_INVALID, clientMessageId);
        }
        String trimmed = content.asText().trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_CONTENT) {
            throw error(ErrorCode.CHAT_MESSAGE_INVALID, clientMessageId);
        }
        // clientMessageId는 정규화 전 원문 길이 기준 (공백 키 우회 차단 — 서비스와 동일 규약)
        JsonNode id = body.get("clientMessageId");
        if (id != null && id.isTextual()
                && id.asText().length() > MAX_CLIENT_MESSAGE_ID) {
            throw error(ErrorCode.CHAT_MESSAGE_INVALID, clientMessageId);
        }
    }

    /** 봉투 탑재용 선추출 — 검증은 validatePayload가 한다. 파싱 실패·비문자열이면 null. */
    private String extractClientMessageId(Object payload) {
        try {
            JsonNode body = payload instanceof byte[] bytes ? OBJECT_MAPPER.readTree(bytes) : null;
            JsonNode id = body != null ? body.get("clientMessageId") : null;
            return id != null && id.isTextual() ? id.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long userId(Principal principal) {
        if (principal == null) {
            return null;
        }
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
