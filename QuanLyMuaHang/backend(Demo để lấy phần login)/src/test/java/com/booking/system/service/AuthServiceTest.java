package com.booking.system.service;

import com.booking.system.entity.User;
import com.booking.system.enums.RoleEnum;
import com.booking.system.enums.UserStatus;
import com.booking.system.repository.UserRepository;
import com.booking.system.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final long NINETY_DAYS_MS = 7_776_000_000L;

    @Mock private UserRepository userRepository;
    @Mock private JwtUtils jwtUtils;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OtpService otpService;
    @Mock private OtpMailService otpMailService;

    @InjectMocks private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshExpirationMs", NINETY_DAYS_MS);
    }

    @Test
    void loginStoresRefreshTokenForNinetyDaysInDeviceSpecificSession() {
        User user = activeUser();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", user.getPassword())).thenReturn(true);
        when(jwtUtils.generateAccessToken(user.getEmail(), user.getRole().name())).thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(eq(user.getEmail()), anyString())).thenReturn("refresh-token");

        var response = authService.authenticate(user.getEmail(), "123456");

        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.argThat(key -> key.startsWith("refreshToken:" + user.getEmail() + ":")),
                eq("refresh-token"),
                eq(NINETY_DAYS_MS),
                eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void refreshKeepsTheSameDeviceSessionWithoutInvalidatingOtherDevices() {
        User user = activeUser();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String oldToken = "old-refresh-token";
        String sessionId = "ios-device-session";
        String key = "refreshToken:" + user.getEmail() + ":" + sessionId;

        when(jwtUtils.validateJwtToken(oldToken)).thenReturn(true);
        when(jwtUtils.getEmailFromJwtToken(oldToken)).thenReturn(user.getEmail());
        when(jwtUtils.getSessionIdFromRefreshToken(oldToken)).thenReturn(sessionId);
        when(valueOperations.get(key)).thenReturn(oldToken);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(jwtUtils.generateAccessToken(user.getEmail(), user.getRole().name())).thenReturn("new-access-token");
        when(jwtUtils.generateRefreshToken(user.getEmail(), sessionId)).thenReturn("new-refresh-token");

        var response = authService.refreshToken(oldToken);

        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        verify(valueOperations).set(key, "new-refresh-token", NINETY_DAYS_MS, TimeUnit.MILLISECONDS);
    }

    @Test
    void refreshMigratesLegacyTokenWithoutForcingExistingUserToLoginAgain() {
        User user = activeUser();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String oldToken = "legacy-refresh-token";
        String legacyKey = "refreshToken:" + user.getEmail();

        when(jwtUtils.validateJwtToken(oldToken)).thenReturn(true);
        when(jwtUtils.getEmailFromJwtToken(oldToken)).thenReturn(user.getEmail());
        when(jwtUtils.getSessionIdFromRefreshToken(oldToken)).thenReturn(null);
        when(valueOperations.get(legacyKey)).thenReturn(oldToken);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(jwtUtils.generateAccessToken(user.getEmail(), user.getRole().name())).thenReturn("new-access-token");
        when(jwtUtils.generateRefreshToken(eq(user.getEmail()), anyString())).thenReturn("new-refresh-token");

        authService.refreshToken(oldToken);

        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.argThat(key -> key.startsWith(legacyKey + ":")),
                eq("new-refresh-token"),
                eq(NINETY_DAYS_MS),
                eq(TimeUnit.MILLISECONDS));
        verify(redisTemplate).delete(legacyKey);
    }

    @Test
    void employeeCannotLoginEvenWithCorrectPassword() {
        User user = activeUser();
        user.setRole(RoleEnum.EMPLOYEE);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", user.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.authenticate(user.getEmail(), "123456"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("nhân viên không được phép đăng nhập");
    }

    @Test
    void employeeCannotRefreshAnExistingSession() {
        User user = activeUser();
        user.setRole(RoleEnum.EMPLOYEE);
        String refreshToken = "employee-refresh-token";
        String sessionId = "old-employee-session";
        String key = "refreshToken:" + user.getEmail() + ":" + sessionId;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtUtils.validateJwtToken(refreshToken)).thenReturn(true);
        when(jwtUtils.getEmailFromJwtToken(refreshToken)).thenReturn(user.getEmail());
        when(jwtUtils.getSessionIdFromRefreshToken(refreshToken)).thenReturn(sessionId);
        when(valueOperations.get(key)).thenReturn(refreshToken);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refreshToken(refreshToken))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("nhân viên không được phép đăng nhập");
    }

    private User activeUser() {
        User user = new User();
        user.setId("user-1");
        user.setEmail("user@example.com");
        user.setFullName("Người dùng");
        user.setPassword("encoded-password");
        user.setRole(RoleEnum.MANAGER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
