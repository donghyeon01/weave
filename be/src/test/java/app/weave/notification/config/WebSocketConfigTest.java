package app.weave.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.weave.auth.jwt.JwtProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

/** STOMP CONNECT 인증 인터셉터 단위 테스트 — 계약 §3.9 CONNECT 규칙. */
class WebSocketConfigTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef";

    private final JwtProvider jwtProvider = new JwtProvider(SECRET);
    private final WebSocketConfig.JwtConnectInterceptor interceptor =
            new WebSocketConfig.JwtConnectInterceptor(jwtProvider);
    private final MessageChannel channel = org.mockito.Mockito.mock(MessageChannel.class);

    private Message<byte[]> connectMessage(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void 유효한_bearer_토큰은_Principal을_userId로_설정한다() {
        String token = jwtProvider.createAccessToken(42L, List.of("ROLE_USER"));

        Message<?> result = interceptor.preSend(connectMessage("Bearer " + token), channel);

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("42");
    }

    @Test
    void 헤더_없는_CONNECT는_거부한다() {
        assertThatThrownBy(() -> interceptor.preSend(connectMessage(null), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void 위조된_토큰의_CONNECT는_거부한다() {
        JwtProvider otherKey = new JwtProvider(
                "ffffffffffffffffffffffffffffffffffffffffffffffff");
        String forged = otherKey.createAccessToken(42L, List.of("ROLE_USER"));

        assertThatThrownBy(
                () -> interceptor.preSend(connectMessage("Bearer " + forged), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void CONNECT_외_프레임은_검증없이_통과한다() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        Message<byte[]> message =
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }
}
