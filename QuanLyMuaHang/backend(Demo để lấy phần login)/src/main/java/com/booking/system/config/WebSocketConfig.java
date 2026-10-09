package com.booking.system.config;

import com.booking.system.entity.User;
import com.booking.system.enums.UserStatus;
import com.booking.system.enums.RoleEnum;
import com.booking.system.hr.service.HrOcrSocketPolicy;
import com.booking.system.repository.UserRepository;
import com.booking.system.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && !HrOcrSocketPolicy.allowed(accessor.getCommand(), accessor.getDestination())) {
                    throw new org.springframework.security.access.AccessDeniedException("Không được truy cập kênh OCR này.");
                }
                if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                        && "/user/queue/ocr-capture".equals(accessor.getDestination())) {
                    User current = accessor.getUser() == null ? null : userRepository.findById(accessor.getUser().getName()).orElse(null);
                    if (current == null || current.getStatus() != UserStatus.ACTIVE
                            || (current.getRole() != RoleEnum.ADMIN && current.getRole() != RoleEnum.MANAGER)) {
                        throw new org.springframework.security.access.AccessDeniedException("Cần tài khoản HR đang hoạt động.");
                    }
                }
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String token = resolveBearerToken(accessor.getNativeHeader("Authorization"));
                    if (token == null || !jwtUtils.validateJwtToken(token)) {
                        throw new IllegalArgumentException("JWT không hợp lệ");
                    }
                    String email = jwtUtils.getEmailFromJwtToken(token);
                    User user = userRepository.findByEmail(email)
                            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user"));
                    accessor.setUser(user::getId);
                }
                return message;
            }
        });
    }

    private String resolveBearerToken(List<String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        String value = headers.get(0);
        if (value != null && value.startsWith("Bearer ")) {
            return value.substring(7);
        }
        return null;
    }
}
