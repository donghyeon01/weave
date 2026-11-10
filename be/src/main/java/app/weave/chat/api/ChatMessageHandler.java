package app.weave.chat.api;

import app.weave.chat.service.ChatService;
import app.weave.chat.service.ChatService.ChatMessageView;
import app.weave.chat.service.StompRoomAuthInterceptor;
import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * api-contract §3.10 STOMP 송신 — SEND /app/rooms/{id}를 받아
 * /topic/rooms/{id}로 이력 응답과 동형(ChatMessageView)을 브로드캐스트한다.
 * 멤버십·CLOSED·payload 검증은 StompRoomAuthInterceptor가 선행하고, 서비스가 재검증한다.
 * 잔여 리스크: 인터셉터 통과 후 close된 방에 도달한 send는 서비스 예외 → ERROR 프레임 경로.
 * @MessageExceptionHandler 없이도 MessagingException 변환으로 계약 ERROR(code)를 유지한다.
 */
@Controller
public class ChatMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageHandler.class);

    public record SendPayload(String content, String clientMessageId) {
    }

    private final ChatService chatService;
    private final SimpMessagingTemplate template;

    public ChatMessageHandler(ChatService chatService, SimpMessagingTemplate template) {
        this.chatService = chatService;
        this.template = template;
    }

    @MessageMapping("/rooms/{roomId}")
    public void send(@DestinationVariable Long roomId, @Payload SendPayload payload,
            Principal principal) {
        Long me = Long.valueOf(principal.getName());
        ChatMessageView view;
        try {
            view = chatService.send(me, roomId, payload.content(), payload.clientMessageId());
        } catch (BusinessException e) {
            throw StompRoomAuthInterceptor.error(e.getErrorCode(), payload.clientMessageId());
        } catch (RuntimeException e) {
            // 비도메인 예외는 내부 메시지가 ERROR 프레임에 새지 않도록 봉투로 변환한다
            log.warn("chat send 실패 roomId={} userId={}", roomId, me, e);
            throw StompRoomAuthInterceptor.error(ErrorCode.INTERNAL_ERROR,
                    payload.clientMessageId());
        }
        template.convertAndSend("/topic/rooms/" + roomId, view);
    }
}
