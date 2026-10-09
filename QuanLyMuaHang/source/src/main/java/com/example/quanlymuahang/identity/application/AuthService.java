package com.example.quanlymuahang.identity.application;

import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountJpaRepository;
import com.example.quanlymuahang.identity.infrastructure.security.AccountPrincipal;
import com.example.quanlymuahang.identity.infrastructure.security.SessionRevocationService;
import com.example.quanlymuahang.sharedkernel.application.AuditRecorder;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserAccountJpaRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder audit;
    private final SessionRevocationService sessions;

    public AuthService(UserAccountJpaRepository accounts, PasswordEncoder passwordEncoder, AuditRecorder audit, SessionRevocationService sessions) {
        this.accounts = accounts; this.passwordEncoder = passwordEncoder; this.audit = audit; this.sessions = sessions;
    }

    @Transactional
    public void recordLoginSuccess(String email) {
        UserAccountEntity account = accounts.findByEmail(UserAccountEntity.canonicalEmail(email))
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        account.recordSuccessfulLogin();
        audit.record(account.getId(), "AUTH_LOGIN_SUCCEEDED", "USER_ACCOUNT", account.getId(), null);
    }

    @Transactional
    public void recordLoginFailure(String email) {
        accounts.findByEmail(UserAccountEntity.canonicalEmail(email)).ifPresent(account -> {
            account.recordFailedLogin();
            audit.record(null, "AUTH_LOGIN_FAILED", "USER_ACCOUNT", account.getId(), java.util.Map.of("reason", "INVALID_CREDENTIALS"));
        });
    }

    @Transactional
    public AccountPrincipal changePassword(AccountPrincipal principal, String currentPassword, String newPassword, String currentSessionId) {
        if (newPassword == null || newPassword.length() < 12)
            throw ApiException.badRequest("WEAK_PASSWORD", "Mật khẩu mới phải có ít nhất 12 ký tự");
        UserAccountEntity account = accounts.findById(principal.id()).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash()))
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Mật khẩu hiện tại không đúng");
        if (passwordEncoder.matches(newPassword, account.getPasswordHash()))
            throw ApiException.badRequest("PASSWORD_REUSE", "Mật khẩu mới phải khác mật khẩu hiện tại");
        account.changePassword(passwordEncoder.encode(newPassword), false);
        audit.record(account.getId(), "USER_PASSWORD_CHANGED", "USER_ACCOUNT", account.getId(), java.util.Map.of("mustChangePassword", false));
        sessions.revokeExcept(account.getEmail(), currentSessionId);
        return new AccountPrincipal(account);
    }
}
