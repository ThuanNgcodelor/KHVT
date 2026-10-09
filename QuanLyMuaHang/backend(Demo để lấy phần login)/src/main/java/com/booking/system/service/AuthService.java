package com.booking.system.service;

import com.booking.system.dto.AuthResponse;
import com.booking.system.entity.User;
import com.booking.system.enums.RoleEnum;
import com.booking.system.enums.UserStatus;
import com.booking.system.repository.UserRepository;
import com.booking.system.security.JwtUtils;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.booking.system.event.NotificationEvent;
import com.booking.system.enums.NotificationPriority;
import com.booking.system.enums.NotificationType;

import java.util.Collections;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final RedisTemplate<String, String> redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final OtpMailService otpMailService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${spring.security.oauth2.client.registration.google.client-id:YOUR_GOOGLE_CLIENT_ID}")
    private String googleClientId;

    @Value("${jwt.refresh.expiration:7776000000}")
    private long refreshExpirationMs;

    public AuthResponse authenticate(String email, String password) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new RuntimeException("Email hoặc mật khẩu không chính xác"));

        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Email hoặc mật khẩu không chính xác");
        }

        requireActiveAccount(user);
        requireLoginRole(user);

        return generateTokensForUser(user);
    }

    // Gửi OTP đăng ký cho email chưa tồn tại trong hệ thống.
    public void requestRegisterOtp(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new RuntimeException("Email đã tồn tại trong hệ thống");
        }

        String otp = otpService.generateAndStoreOtp(otpService.registerKey(normalizedEmail));
        otpMailService.sendRegisterOtp(normalizedEmail, otp);
    }

    // Xác thực OTP và tạo tài khoản quản lý đang chờ Admin phê duyệt.
    @Transactional
    public void verifyRegisterOtp(String email, String otp, String fullName, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            otpService.deleteOtp(otpService.registerKey(normalizedEmail));
            throw new RuntimeException("Email đã tồn tại trong hệ thống");
        }

        otpService.verifyOtp(otpService.registerKey(normalizedEmail), otp);

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setFullName(fullName.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(RoleEnum.MANAGER);
        user.setStatus(UserStatus.PENDING_APPROVAL);
        User saved = userRepository.save(user);

        eventPublisher.publishEvent(new NotificationEvent(
                saved.getId(),
                null,
                NotificationType.ACCOUNT_REGISTRATION_PENDING,
                "Tài khoản đang chờ phê duyệt",
                "Email của bạn đã được xác minh. Tài khoản đang chờ quản trị viên phê duyệt.",
                "/login",
                "ACCOUNT_REGISTRATION",
                saved.getId(),
                NotificationPriority.NORMAL,
                new NotificationEvent.EmailInstruction(
                        NotificationEvent.EmailType.ACCOUNT_REGISTRATION_PENDING,
                        "tài khoản", saved.getFullName(), "Đang chờ phê duyệt", null)
        ));

        for (User admin : userRepository.findByRole(RoleEnum.ADMIN)) {
            eventPublisher.publishEvent(new NotificationEvent(
                    admin.getId(),
                    saved.getId(),
                    NotificationType.ACCOUNT_REGISTRATION_REQUESTED,
                    "Tài khoản mới chờ phê duyệt",
                    saved.getFullName() + " (" + saved.getEmail() + ") đã xác minh email và đăng ký tài khoản.",
                    "/admin/users?tab=pending",
                    "ACCOUNT_REGISTRATION",
                    saved.getId(),
                    NotificationPriority.HIGH,
                    null
            ));
        }
    }

    // Gửi OTP đặt lại mật khẩu cho email đang tồn tại.
    public void requestForgotPasswordOtp(String email) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RuntimeException("Email không tồn tại trong hệ thống"));
        requireLoginRole(user);

        String otp = otpService.generateAndStoreOtp(otpService.forgotPasswordKey(normalizedEmail));
        otpMailService.sendForgotPasswordOtp(normalizedEmail, otp);
    }

    // Xác thực OTP và cập nhật mật khẩu mới, không thay đổi thông tin profile.
    public void resetPasswordWithOtp(String email, String otp, String newPassword) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RuntimeException("Email không tồn tại trong hệ thống"));
        requireLoginRole(user);

        otpService.verifyOtp(otpService.forgotPasswordKey(normalizedEmail), otp);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public AuthResponse authenticateWithGoogle(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);

            if (idToken == null) {
                throw new RuntimeException("Token Google không hợp lệ");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");

            return processUserAuth(email, name, pictureUrl);

        } catch (Exception e) {
            throw new RuntimeException("Lỗi xác thực Google: " + e.getMessage());
        }
    }

    public AuthResponse authenticateDummyForDev(String email) {
        return processUserAuth(email, "Developer User", "");
    }

    private AuthResponse processUserAuth(String email, String name, String pictureUrl) {
        Optional<User> userOpt = userRepository.findByEmail(normalizeEmail(email));
        User user;

        if (userOpt.isPresent()) {
            user = userOpt.get();
            requireActiveAccount(user);
            requireLoginRole(user);
            // Cập nhật tên/avatar mới nhất từ Google
            user.setFullName(name);
            user.setAvatarUrl(pictureUrl);
            user = userRepository.save(user);
        } else {
            throw new RuntimeException("Tài khoản chưa tồn tại. Vui lòng đăng ký bằng email và xác minh OTP");
        }

        return generateTokensForUser(user);
    }

    private AuthResponse generateTokensForUser(User user) {
        requireLoginRole(user);
        String accessToken = jwtUtils.generateAccessToken(user.getEmail(), user.getRole().name());
        String sessionId = UUID.randomUUID().toString();
        String refreshToken = jwtUtils.generateRefreshToken(user.getEmail(), sessionId);

        redisTemplate.opsForValue().set(
                refreshTokenKey(user.getEmail(), sessionId),
                refreshToken,
                refreshExpirationMs,
                TimeUnit.MILLISECONDS
        );

        AuthResponse.UserDto userDto = new AuthResponse.UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getAvatarUrl(),
                user.getDepartment() == null ? null : user.getDepartment().getId(),
                user.getDepartment() == null ? null : user.getDepartment().getName(),
                user.getJobPosition(),
                user.getPassword() != null
        );

        return new AuthResponse(accessToken, refreshToken, userDto);
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtUtils.validateJwtToken(refreshToken)) {
            throw new RuntimeException("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        String email = jwtUtils.getEmailFromJwtToken(refreshToken);
        String currentSessionId = jwtUtils.getSessionIdFromRefreshToken(refreshToken);
        String currentTokenKey = currentSessionId == null
                ? legacyRefreshTokenKey(email)
                : refreshTokenKey(email, currentSessionId);
        String savedToken = redisTemplate.opsForValue().get(currentTokenKey);

        if (savedToken == null || !savedToken.equals(refreshToken)) {
            throw new RuntimeException("Refresh token không khớp hoặc đã bị thu hồi");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));
        requireActiveAccount(user);
        requireLoginRole(user);

        // Cấp lại token mới
        String newAccessToken = jwtUtils.generateAccessToken(user.getEmail(), user.getRole().name());
        String newSessionId = currentSessionId == null ? UUID.randomUUID().toString() : currentSessionId;
        String newRefreshToken = jwtUtils.generateRefreshToken(user.getEmail(), newSessionId);

        redisTemplate.opsForValue().set(
                refreshTokenKey(email, newSessionId),
                newRefreshToken,
                refreshExpirationMs,
                TimeUnit.MILLISECONDS);
        if (currentSessionId == null) {
            redisTemplate.delete(currentTokenKey);
        }

        AuthResponse.UserDto userDto = new AuthResponse.UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getAvatarUrl(),
                user.getDepartment() == null ? null : user.getDepartment().getId(),
                user.getDepartment() == null ? null : user.getDepartment().getName(),
                user.getJobPosition(),
                user.getPassword() != null
        );

        return new AuthResponse(newAccessToken, newRefreshToken, userDto);
    }

    public void logout(String refreshToken) {
        try {
            if (jwtUtils.validateJwtToken(refreshToken)) {
                String email = jwtUtils.getEmailFromJwtToken(refreshToken);
                String sessionId = jwtUtils.getSessionIdFromRefreshToken(refreshToken);
                redisTemplate.delete(sessionId == null
                        ? legacyRefreshTokenKey(email)
                        : refreshTokenKey(email, sessionId));
            }
        } catch (Exception e) {
            // Bỏ qua lỗi token khi logout phía client.
        }
    }

    public void revokeAllSessions(String email) {
        String normalizedEmail = normalizeEmail(email);
        redisTemplate.delete(legacyRefreshTokenKey(normalizedEmail));
        Set<String> deviceKeys = redisTemplate.keys(refreshTokenKey(normalizedEmail, "*"));
        if (deviceKeys != null && !deviceKeys.isEmpty()) {
            redisTemplate.delete(deviceKeys);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private void requireActiveAccount(User user) {
        if (user.getStatus() == UserStatus.PENDING_APPROVAL) {
            throw new RuntimeException("Tài khoản đang chờ quản trị viên phê duyệt");
        }
        if (user.getStatus() == UserStatus.REJECTED) {
            throw new RuntimeException("Yêu cầu đăng ký tài khoản đã bị từ chối");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new RuntimeException("Tài khoản đã bị khóa");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("Tài khoản chưa được kích hoạt");
        }
    }

    private void requireLoginRole(User user) {
        if (user.getRole() != RoleEnum.ADMIN && user.getRole() != RoleEnum.MANAGER) {
            throw new RuntimeException("Tài khoản nhân viên không được phép đăng nhập hệ thống quản lý nhân sự");
        }
    }

    private String refreshTokenKey(String email, String sessionId) {
        return "refreshToken:" + email + ":" + sessionId;
    }

    private String legacyRefreshTokenKey(String email) {
        return "refreshToken:" + email;
    }
}
