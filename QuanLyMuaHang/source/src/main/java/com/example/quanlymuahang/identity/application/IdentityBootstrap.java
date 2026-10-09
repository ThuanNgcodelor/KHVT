package com.example.quanlymuahang.identity.application;

import com.example.quanlymuahang.identity.infrastructure.persistence.AccountStatus;
import com.example.quanlymuahang.identity.infrastructure.persistence.RoleEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.RoleJpaRepository;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
public class IdentityBootstrap implements ApplicationRunner {
    private final UserAccountJpaRepository accounts;
    private final RoleJpaRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String displayName;

    public IdentityBootstrap(UserAccountJpaRepository accounts, RoleJpaRepository roles, PasswordEncoder passwordEncoder,
                             @Value("${app.bootstrap-admin.email:}") String email,
                             @Value("${app.bootstrap-admin.password:}") String password,
                             @Value("${app.bootstrap-admin.display-name:Quản trị viên}") String displayName) {
        this.accounts = accounts; this.roles = roles; this.passwordEncoder = passwordEncoder;
        this.email = email; this.password = password; this.displayName = displayName;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.isBlank()) throw new IllegalStateException("Cần cấu hình đồng thời ADMIN_BOOTSTRAP_EMAIL và ADMIN_BOOTSTRAP_PASSWORD");
        if (password.length() < 12) throw new IllegalStateException("ADMIN_BOOTSTRAP_PASSWORD phải có ít nhất 12 ký tự");
        String canonicalEmail = UserAccountEntity.canonicalEmail(email);
        if (accounts.existsByEmail(canonicalEmail)) return;
        RoleEntity admin = roles.findByCode("ADMIN").orElseThrow(() -> new IllegalStateException("Role ADMIN chưa được khởi tạo"));
        UserAccountEntity account = new UserAccountEntity(canonicalEmail, displayName, passwordEncoder.encode(password));
        account.setStatus(AccountStatus.ACTIVE);
        account.changePassword(passwordEncoder.encode(password), true);
        account.replaceRoles(Set.of(admin));
        accounts.save(account);
    }
}
