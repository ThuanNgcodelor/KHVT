package com.example.quanlymuahang.identity.application;

import com.example.quanlymuahang.identity.infrastructure.notification.EmailSenderService;
import com.example.quanlymuahang.identity.infrastructure.persistence.AccountStatus;
import com.example.quanlymuahang.identity.infrastructure.persistence.PasswordResetTokenEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.PasswordResetTokenJpaRepository;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountJpaRepository;
import com.example.quanlymuahang.identity.infrastructure.security.RememberMeService;
import com.example.quanlymuahang.identity.infrastructure.security.SessionRevocationService;
import com.example.quanlymuahang.sharedkernel.application.AuditRecorder;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountJpaRepository accounts;
    private final PasswordResetTokenJpaRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final EmailSenderService emailSender;
    private final SessionRevocationService sessionRevocation;
    private final RememberMeService rememberMeService;
    private final AuditRecorder audit;

    private final String resetPasswordBaseUrl;
    private final int tokenValidityMinutes;

    public PasswordResetService(UserAccountJpaRepository accounts,
                                PasswordResetTokenJpaRepository tokens,
                                PasswordEncoder passwordEncoder,
                                EmailSenderService emailSender,
                                SessionRevocationService sessionRevocation,
                                @Autowired(required = false) RememberMeService rememberMeService,
                                AuditRecorder audit,
                                @Value("${app.mail.reset-password-base-url:http://localhost:5173/reset-password}") String resetPasswordBaseUrl,
                                @Value("${app.mail.token-validity-minutes:15}") int tokenValidityMinutes) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.sessionRevocation = sessionRevocation;
        this.rememberMeService = rememberMeService;
        this.audit = audit;
        this.resetPasswordBaseUrl = resetPasswordBaseUrl;
        this.tokenValidityMinutes = tokenValidityMinutes;
    }

    @Transactional
    public void requestReset(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) return;
        String email = UserAccountEntity.canonicalEmail(rawEmail);
        Optional<UserAccountEntity> userOpt = accounts.findByEmail(email);

        if (userOpt.isEmpty()) {
            log.info("Password reset requested for non-existing email: {}", email);
            return;
        }

        UserAccountEntity user = userOpt.get();
        if (user.getStatus() != AccountStatus.ACTIVE) {
            log.warn("Password reset requested for inactive/locked user: {}", email);
            return;
        }

        // Invalidate previous tokens
        tokens.invalidateAllForUser(user.getId(), Instant.now());

        // Generate cryptographically secure token
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = hash(rawToken);

        Instant expiresAt = Instant.now().plus(tokenValidityMinutes, ChronoUnit.MINUTES);
        PasswordResetTokenEntity tokenEntity = new PasswordResetTokenEntity(user.getId(), tokenHash, expiresAt);
        tokens.save(tokenEntity);

        String resetUrl = resetPasswordBaseUrl + (resetPasswordBaseUrl.contains("?") ? "&" : "?") + "token=" + rawToken;
        emailSender.sendPasswordResetEmail(user.getEmail(), user.getDisplayName(), resetUrl, tokenValidityMinutes);

        audit.record(user.getId(), "AUTH_PASSWORD_RESET_REQUESTED", "USER_ACCOUNT", user.getId(),
                Map.of("email", user.getEmail()));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.badRequest("INVALID_TOKEN", "Mã đặt lại mật khẩu không hợp lệ");
        }
        if (newPassword == null || newPassword.length() < 12 || newPassword.length() > 200) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Mật khẩu mới phải từ 12 đến 200 ký tự");
        }

        String tokenHash = hash(rawToken.trim());
        PasswordResetTokenEntity tokenEntity = tokens.findByTokenHash(tokenHash)
                .orElseThrow(() -> ApiException.badRequest("INVALID_OR_EXPIRED_TOKEN", "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"));

        if (!tokenEntity.isValid()) {
            throw ApiException.badRequest("INVALID_OR_EXPIRED_TOKEN", "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }

        UserAccountEntity user = accounts.findById(tokenEntity.getUserId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản người dùng"));

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw ApiException.forbidden("Tài khoản hiện đang bị khóa hoặc ngưng hoạt động");
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("PASSWORD_REUSE", "Mật khẩu mới phải khác mật khẩu trước đó");
        }

        // Update password and clear mustChangePassword flag (resets failedLoginCount & lockedUntil automatically)
        user.changePassword(passwordEncoder.encode(newPassword), false);
        tokenEntity.markUsed();

        // Invalidate active sessions and remember-me tokens across all devices for security
        sessionRevocation.revokeAll(user.getEmail());
        if (rememberMeService != null) {
            rememberMeService.revokeAllForUser(user.getId());
        }

        audit.record(user.getId(), "AUTH_PASSWORD_RESET_COMPLETED", "USER_ACCOUNT", user.getId(),
                Map.of("email", user.getEmail()));
    }

    private static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }
}
