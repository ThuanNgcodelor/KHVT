package com.example.quanlymuahang.identity.application;

import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountJpaRepository;
import com.example.quanlymuahang.identity.infrastructure.security.AccountPrincipal;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserAccountJpaRepository accounts;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserAccountJpaRepository accounts, PasswordEncoder passwordEncoder) {
        this.accounts = accounts; this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void recordLoginSuccess(String email) {
        UserAccountEntity account = accounts.findByEmail(UserAccountEntity.canonicalEmail(email))
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        account.recordSuccessfulLogin();
    }

    @Transactional
    public void recordLoginFailure(String email) {
        accounts.findByEmail(UserAccountEntity.canonicalEmail(email)).ifPresent(UserAccountEntity::recordFailedLogin);
    }

    @Transactional
    public void changePassword(AccountPrincipal principal, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 12)
            throw ApiException.badRequest("WEAK_PASSWORD", "Mật khẩu mới phải có ít nhất 12 ký tự");
        UserAccountEntity account = accounts.findById(principal.id()).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash()))
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Mật khẩu hiện tại không đúng");
        if (passwordEncoder.matches(newPassword, account.getPasswordHash()))
            throw ApiException.badRequest("PASSWORD_REUSE", "Mật khẩu mới phải khác mật khẩu hiện tại");
        account.changePassword(passwordEncoder.encode(newPassword), false);
    }
}
