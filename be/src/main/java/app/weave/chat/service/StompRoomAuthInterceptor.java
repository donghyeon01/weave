package app.weave.chat.service;

import app.weave.chat.domain.ChatRoom;
import app.weave.chat.domain.ChatRoom.Status;
import app.weave.chat.domain.ChatRoomRepository;
import app.weave.common.error.ErrorCode;
import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * chat 목적지의 멤버십·방 상태 검증 (api-contract §3.10).
 * SEND /app/rooms/{id} · SUBSCRIBE /topic/rooms/{id}만 검사하고 다른 목적지는 통과한다.
 * 실패는 MessagingException → STOMP ERROR 프레임(message 헤더에 {"code":"..."} JSON) 후
 * 해당 프레임만 거부 — 연결은 유지되어 notification 구독에 영향이 없다.
 */
@Component
public class StompRoomAuthInterceptor implements ChannelInterceptor {

    private static final Pattern SEND_ROOM = Pattern.compile("^/app/rooms/(\\d+)$");
    private static final Pattern SUBSCRIBE_ROOM = Pattern.compile("^/topic/rooms/(\\d+)$");

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
        return new MessagingException("{\"code\":\"" + code.getCode() + "\"}");
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getDestination() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        Matcher matcher = command == StompCommand.SEND
                ? SEND_ROOM.matcher(accessor.getDestination())
                : command == StompCommand.SUBSCRIBE
                        ? SUBSCRIBE_ROOM.matcher(accessor.getDestination())
                        : null;
        if (matcher == null || !matcher.matches()) {
            return message;
        }
        Long roomId = Long.valueOf(matcher.group(1));
        ChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> error(ErrorCode.CHAT_ROOM_NOT_FOUND));
        Long me = userId(accessor.getUser());
        if (me == null || !chatService.isMember(roomId, me)) {
            throw error(ErrorCode.CHAT_NOT_MEMBER);
        }
        // CLOSED 방은 송신만 차단 — 멤버의 구독은 허용해 종료 통지·기존 수신 흐름을 유지한다
        if (command == StompCommand.SEND && room.getStatus() == Status.CLOSED) {
            throw error(ErrorCode.CHAT_ROOM_CLOSED);
        }
        return message;
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
