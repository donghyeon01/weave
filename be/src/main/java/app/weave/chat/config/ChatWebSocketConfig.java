package app.weave.chat.config;

import app.weave.chat.service.StompRoomAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * api-contract §3.10 chat STOMP 송신 채널 — notification WebSocketConfig에 합산되는 configurer.
 * 브로커 목적지(/queue,/user,/topic)는 WebSocketConfig 한 곳에서만 선언한다 —
 * 별도 SimpleBroker 빈은 CONNECT(목적지 없음)도 처리해 CONNECTED 프레임이 중복된다.
 * 여기서는 /app prefix와 멤버십 인터셉터만 등록한다.
 */
@Configuration
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompRoomAuthInterceptor roomAuthInterceptor;

    public ChatWebSocketConfig(StompRoomAuthInterceptor roomAuthInterceptor) {
        this.roomAuthInterceptor = roomAuthInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(roomAuthInterceptor);
    }
}
