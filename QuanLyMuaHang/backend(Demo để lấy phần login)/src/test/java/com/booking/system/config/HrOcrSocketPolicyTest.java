package com.booking.system.config;

import com.booking.system.entity.User;
import com.booking.system.enums.*;
import com.booking.system.repository.UserRepository;
import com.booking.system.security.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HrOcrSocketPolicyTest {
    JwtUtils jwt = mock(JwtUtils.class);
    UserRepository users = mock(UserRepository.class);
    ChannelInterceptor interceptor;
    @BeforeEach void setup() {
        new WebSocketConfig(jwt, users).configureClientInboundChannel(new ChannelRegistration() {
            @Override public ChannelRegistration interceptors(ChannelInterceptor... list) {
                interceptor = list[0]; return this;
            }
        });
    }
    StompHeaderAccessor headers(StompCommand command, String destination) {
        var h = StompHeaderAccessor.create(command); h.setLeaveMutable(true);
        if (destination != null) h.setDestination(destination);
        return h;
    }
    void send(StompHeaderAccessor h) { interceptor.preSend(MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()), null); }
    @Test void connectMustValidateJwtAndUsesDatabaseUserIdAsPrincipal() {
        var h = headers(StompCommand.CONNECT, null);
        assertThatThrownBy(() -> send(h)).hasMessageContaining("JWT");
        User user = new User(); user.setId("u1"); user.setEmail("hr@example.test");
        when(jwt.validateJwtToken("test-token")).thenReturn(true);
        when(jwt.getEmailFromJwtToken("test-token")).thenReturn(user.getEmail());
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        h.setNativeHeader("Authorization", "Bearer test-token"); send(h);
        assertThat(h.getUser().getName()).isEqualTo("u1");
    }
    @Test void onlyAnActiveHrPrincipalCanSubscribeToItsOwnLogicalOcrQueue() {
        var h = headers(StompCommand.SUBSCRIBE, "/user/queue/ocr-capture");
        assertThatThrownBy(() -> send(h)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        User user = new User(); user.setId("u1"); user.setRole(RoleEnum.MANAGER); user.setStatus(UserStatus.ACTIVE);
        when(users.findById("u1")).thenReturn(Optional.of(user)); h.setUser(user::getId);
        assertThatCode(() -> send(h)).doesNotThrowAnyException();
        user.setStatus(UserStatus.INACTIVE);
        assertThatThrownBy(() -> send(h)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        user.setStatus(UserStatus.ACTIVE); user.setRole(RoleEnum.EMPLOYEE);
        assertThatThrownBy(() -> send(h)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void rejectsForgedMessagesRawPrivateQueuesAndWildcardSubscriptions() {
        for (String path : new String[]{"/queue/ocr-capture-user1", "/user/u2/queue/ocr-capture", "/queue/**", "/**"}) {
            assertThatThrownBy(() -> send(headers(StompCommand.SUBSCRIBE, path))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
        assertThatThrownBy(() -> send(headers(StompCommand.SEND, "/user/queue/ocr-capture"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatCode(() -> send(headers(StompCommand.SUBSCRIBE, "/user/queue/notifications"))).doesNotThrowAnyException();
    }
}
