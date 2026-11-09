package app.weave.chat.config;

import app.weave.chat.service.StompRoomAuthInterceptor;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * api-contract §3.10 chat STOMP 송신 채널 — notification WebSocketConfig와 별도 configurer.
 * registry.enableSimpleBroker는 마지막 호출만 유효(덮어쓰기)하고 configurer 실행 순서가
 * 컴포넌트 스캔 순서에 의존하므로, /topic은 등록 순서와 무관한 전용 브로커 빈으로 띄운다.
 * notification의 SimpleBroker(/queue,/user)와 목적지 prefix가 겹치지 않아 상호 무영향.
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

    /**
     * /topic 전용 SimpleBroker — clientInboundChannel(SUBSCRIBE)과 brokerChannel(발행) 양쪽에
     * 구독 등록된다. framework의 /queue,/user 브로커는 /topic 목적지를 무시하므로 충돌 없다.
     */
    @Bean
    public SimpleBrokerMessageHandler chatTopicBroker(
            @Qualifier("clientInboundChannel") SubscribableChannel clientInbound,
            @Qualifier("clientOutboundChannel") SubscribableChannel clientOutbound,
            @Qualifier("brokerChannel") SubscribableChannel brokerChannel) {
        return new SimpleBrokerMessageHandler(clientInbound, clientOutbound, brokerChannel,
                List.of("/topic"));
    }
}
