package com.goodsbridge._core.config;

import com.goodsbridge.exchange.ExchangeChatService;
import lombok.RequiredArgsConstructor;
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

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 교환 채팅용 WebSocket(STOMP). 연결(handshake)은 기존 JWT 쿠키 인증 필터를 그대로 거치므로 로그인한 사용자만 접속된다.
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Pattern EXCHANGE_TOPIC = Pattern.compile("^/topic/exchange-requests/(\\d+)$");

    private final ExchangeChatService exchangeChatService;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    // 구독은 해당 교환의 당사자(A, B)만 가능
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    Principal user = accessor.getUser();
                    String destination = accessor.getDestination();
                    Matcher matcher = destination == null ? null : EXCHANGE_TOPIC.matcher(destination);
                    if (user == null || matcher == null || !matcher.matches()
                        || !exchangeChatService.isParticipant(Long.valueOf(matcher.group(1)), Long.valueOf(user.getName()))) {
                        throw new MessagingException("구독 권한이 없습니다.");
                    }
                }
                return message;
            }
        });
    }
}
