package app.weave.notification.config;

import app.weave.auth.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import java.security.Principal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * api-contract §3.9 WS /ws — STOMP 구독 전용 (클라이언트→서버 메시지 없음).
 * 목록이 원천이고 push는 보조라 applicationDestinationPrefix는 두지 않는다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtProvider jwtProvider;
    private final List<String> allowedOrigins;

    public WebSocketConfig(JwtProvider jwtProvider,
            @Value("${app.cors.allowed-origins:http://localhost:5173}")
            List<String> allowedOrigins) {
        this.jwtProvider = jwtProvider;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // raw WebSocket만 등록 — SockJS 폴백은 계약상 선택 사항
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // enableSimpleBroker는 last-call-wins라 두 번째 브로커 빈은 CONNECTED 중복을 만든다 —
        // 브로커는 이 한 곳, /topic은 chat이 사용.
        // /user는 브로커 prefix가 아니라 user-destination prefix여야 한다 — 브로커에도 넣으면
        // /user/queue/* 원시 구독이 번역 구독(/queue/...-user{session})을 덮어 push가 유실된다.
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new JwtConnectInterceptor(jwtProvider));
    }

    /**
     * CONNECT의 네이티브 헤더 Authorization: Bearer를 검증해 Principal=userId로 설정한다.
     * 실패 시 MessagingException → 클라이언트에 ERROR 프레임 후 연결 종료 (계약 §3.9).
     */
    static class JwtConnectInterceptor implements ChannelInterceptor {

        private final JwtProvider jwtProvider;

        JwtConnectInterceptor(JwtProvider jwtProvider) {
            this.jwtProvider = jwtProvider;
        }

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor =
                    MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
                return message;
            }
            String auth = accessor.getFirstNativeHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) {
                throw new MessagingException("STOMP CONNECT rejected: bearer token required");
            }
            try {
                Claims claims = jwtProvider.parseAccessToken(auth.substring(7));
                accessor.setUser(new StompPrincipal(claims.getSubject()));
                return message;
            } catch (Exception e) {
                throw new MessagingException("STOMP CONNECT rejected: invalid token", e);
            }
        }
    }

    /**
     * 세션 Principal — getName()이 userId 문자열이어야
     * convertAndSendToUser(userId, "/queue/notifications", ...) 라우팅과 일치한다.
     */
    record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
