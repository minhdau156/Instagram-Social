package com.instagram.infrastructure.config;

import com.instagram.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private MessageChannel channel;

    private WebSocketAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthChannelInterceptor(jwtTokenProvider);
    }

    private Message<?> buildConnectMessage(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authHeader != null) {
            accessor.setNativeHeader("Authorization", authHeader);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> buildSubscribeMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void preSend_returnsMessageUnchangedForNonConnectCommand() {
        Message<?> message = buildSubscribeMessage();
        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isSameAs(message);
    }

    @Test
    void preSend_returnsMessageWhenNoAuthHeader() {
        Message<?> message = buildConnectMessage(null);
        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isNotNull();
    }

    @Test
    void preSend_returnsMessageWhenAuthHeaderNotBearer() {
        Message<?> message = buildConnectMessage("Basic dXNlcjpwYXNz");
        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isNotNull();
    }

    @Test
    void preSend_setsUserOnAccessorWhenValidToken() {
        UUID userId = UUID.randomUUID();
        String token = "valid-jwt-token";
        when(jwtTokenProvider.validateAccessToken(token)).thenReturn(Optional.of(userId));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        UsernamePasswordAuthenticationToken auth =
                (UsernamePasswordAuthenticationToken) resultAccessor.getUser();
        assertThat(auth.getPrincipal()).isEqualTo(userId);
    }

    @Test
    void preSend_doesNotSetUserWhenTokenInvalid() {
        String token = "invalid-jwt-token";
        when(jwtTokenProvider.validateAccessToken(token)).thenReturn(Optional.empty());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isNull();
    }
}
