package com.example.quanlymuahang.identity.application;

import com.example.quanlymuahang.identity.infrastructure.persistence.AccountStatus;
import com.example.quanlymuahang.identity.infrastructure.persistence.RoleEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.RoleJpaRepository;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountJpaRepository;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UserAdministrationService {
    private final UserAccountJpaRepository accounts;
    private final RoleJpaRepository roles;
    private final PasswordEncoder encoder;

    public UserAdministrationService(UserAccountJpaRepository accounts, RoleJpaRepository roles, PasswordEncoder encoder) {
        this.accounts = accounts; this.roles = roles; this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public Page<UserView> list(Pageable pageable) { return accounts.findAllByOrderByCreatedAtDesc(pageable).map(UserView::from); }

    @Transactional
    public UserView create(CreateUser command) {
        String email = UserAccountEntity.canonicalEmail(command.email());
        if (accounts.existsByEmail(email)) throw ApiException.conflict("EMAIL_EXISTS", "Email đã được sử dụng");
        if (command.initialPassword().length() < 12) throw ApiException.badRequest("WEAK_PASSWORD", "Mật khẩu tạm phải có ít nhất 12 ký tự");
        UserAccountEntity account = new UserAccountEntity(email, command.displayName(), encoder.encode(command.initialPassword()));
        account.changePassword(encoder.encode(command.initialPassword()), true);
        account.setEmployeeId(command.employeeId());
        account.replaceRoles(resolveRoles(command.roleCodes()));
        return UserView.from(accounts.save(account));
    }

    @Transactional
    public UserView update(long id, UpdateUser command, long actorId) {
        UserAccountEntity account = accounts.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        if (id == actorId && command.status() != AccountStatus.ACTIVE)
            throw ApiException.badRequest("CANNOT_DISABLE_SELF", "Bạn không thể tự khóa hoặc vô hiệu hóa tài khoản của mình");
        Set<RoleEntity> nextRoles = resolveRoles(command.roleCodes());
        boolean removesAdmin = account.getRoles().stream().anyMatch(role -> role.getCode().equals("ADMIN"))
                && nextRoles.stream().noneMatch(role -> role.getCode().equals("ADMIN"));
        boolean willBeInactive = command.status() != AccountStatus.ACTIVE;
        if ((removesAdmin || willBeInactive) && account.getRoles().stream().anyMatch(role -> role.getCode().equals("ADMIN"))
                && accounts.countActiveAdministrators("ADMIN") <= 1)
            throw ApiException.badRequest("LAST_ADMIN", "Không thể gỡ hoặc khóa quản trị viên hoạt động cuối cùng");
        account.setDisplayName(command.displayName());
        account.setStatus(command.status());
        account.setEmployeeId(command.employeeId());
        account.replaceRoles(nextRoles);
        return UserView.from(account);
    }

    @Transactional
    public void resetPassword(long id, String temporaryPassword) {
        if (temporaryPassword == null || temporaryPassword.length() < 12)
            throw ApiException.badRequest("WEAK_PASSWORD", "Mật khẩu tạm phải có ít nhất 12 ký tự");
        UserAccountEntity account = accounts.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        account.changePassword(encoder.encode(temporaryPassword), true);
    }

    private Set<RoleEntity> resolveRoles(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) throw ApiException.badRequest("ROLE_REQUIRED", "Tài khoản phải có ít nhất một vai trò");
        Set<String> codes = roleCodes.stream().map(code -> code.trim().toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        List<RoleEntity> found = roles.findAllByActiveTrueOrderByNameAsc().stream().filter(role -> codes.contains(role.getCode())).toList();
        if (found.size() != codes.size()) throw ApiException.badRequest("UNKNOWN_ROLE", "Một hoặc nhiều vai trò không tồn tại hoặc đã bị ngừng");
        return new HashSet<>(found);
    }

    public record CreateUser(String email, String displayName, String initialPassword, Long employeeId, Set<String> roleCodes) {}
    public record UserView(Long id, String email, String displayName, Long employeeId, AccountStatus status,
                           boolean mustChangePassword, int failedLoginCount, java.time.Instant lockedUntil,
                           java.time.Instant lastLoginAt, Set<String> roleCodes) {
        static UserView from(UserAccountEntity account) {
            return new UserView(account.getId(), account.getEmail(), account.getDisplayName(), account.getEmployeeId(),
                    account.getStatus(), account.isMustChangePassword(), account.getFailedLoginCount(), account.getLockedUntil(),
                    account.getLastLoginAt(), account.getRoles().stream().map(RoleEntity::getCode).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        }
    }
    public record UpdateUser(String displayName, Long employeeId, AccountStatus status, Set<String> roleCodes) {}
}
