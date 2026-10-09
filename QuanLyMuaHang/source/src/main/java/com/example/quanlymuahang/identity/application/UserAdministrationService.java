package com.example.quanlymuahang.identity.application;

import com.example.quanlymuahang.identity.infrastructure.persistence.AccountStatus;
import com.example.quanlymuahang.identity.infrastructure.persistence.RoleEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.RoleJpaRepository;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountJpaRepository;
import com.example.quanlymuahang.personnel.infrastructure.persistence.EmployeeJpaRepository;
import com.example.quanlymuahang.personnel.infrastructure.persistence.EmployeeStatus;
import com.example.quanlymuahang.identity.infrastructure.security.SessionRevocationService;
import com.example.quanlymuahang.sharedkernel.application.AuditRecorder;
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
    private final EmployeeJpaRepository employees;
    private final SessionRevocationService sessions;
    private final AuditRecorder audit;

    public UserAdministrationService(UserAccountJpaRepository accounts, RoleJpaRepository roles, PasswordEncoder encoder,
                                     EmployeeJpaRepository employees, SessionRevocationService sessions, AuditRecorder audit) {
        this.accounts = accounts; this.roles = roles; this.encoder = encoder; this.employees = employees;
        this.sessions = sessions; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<UserView> list(Pageable pageable) { return accounts.findAllByOrderByCreatedAtDesc(pageable).map(UserView::from); }

    @Transactional
    public UserView create(CreateUser command, long actorId) {
        if (command == null || command.email() == null || command.email().isBlank()
                || command.displayName() == null || command.displayName().isBlank()
                || command.initialPassword() == null || command.roleCodes() == null)
            throw ApiException.badRequest("INVALID_USER", "Email, tên hiển thị, mật khẩu tạm và vai trò là bắt buộc");
        String email = UserAccountEntity.canonicalEmail(command.email());
        if (accounts.existsByEmail(email)) throw ApiException.conflict("EMAIL_EXISTS", "Email đã được sử dụng");
        if (command.initialPassword().length() < 12) throw ApiException.badRequest("WEAK_PASSWORD", "Mật khẩu tạm phải có ít nhất 12 ký tự");
        UserAccountEntity account = new UserAccountEntity(email, command.displayName(), command.initialPassword());
        account.changePassword(encoder.encode(command.initialPassword()), true);
        account.setEmployeeId(command.employeeId());
        validateEmployee(command.employeeId());
        account.replaceRoles(resolveRoles(command.roleCodes()));
        UserAccountEntity saved = accounts.save(account);
        UserView view = UserView.from(saved);
        audit.record(actorId, "USER_CREATED", "USER_ACCOUNT", saved.getId(), view);
        return view;
    }

    @Transactional
    public UserView update(long id, UpdateUser command, long actorId) {
        UserAccountEntity account = accounts.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        if (id == actorId && command.status() != AccountStatus.ACTIVE)
            throw ApiException.badRequest("CANNOT_DISABLE_SELF", "Bạn không thể tự khóa hoặc vô hiệu hóa tài khoản của mình");
        Set<RoleEntity> nextRoles = resolveRoles(command.roleCodes());
        validateEmployee(command.employeeId());
        boolean removesAdmin = account.getRoles().stream().anyMatch(role -> role.getCode().equals("ADMIN"))
                && nextRoles.stream().noneMatch(role -> role.getCode().equals("ADMIN"));
        boolean willBeInactive = command.status() != AccountStatus.ACTIVE;
        boolean isAdmin = account.getRoles().stream().anyMatch(role -> role.getCode().equals("ADMIN"));
        if ((removesAdmin || willBeInactive) && isAdmin) {
            roles.lockIdByCode("ADMIN").orElseThrow(() -> new IllegalStateException("Role ADMIN chưa được khởi tạo"));
            if (accounts.lockActiveAdministrators("ADMIN").size() <= 1)
                throw ApiException.badRequest("LAST_ADMIN", "Không thể gỡ hoặc khóa quản trị viên hoạt động cuối cùng");
        }
        account.setDisplayName(command.displayName());
        account.setStatus(command.status());
        account.setEmployeeId(command.employeeId());
        account.replaceRoles(nextRoles);
        UserView view = UserView.from(account);
        audit.record(actorId, "USER_UPDATED", "USER_ACCOUNT", account.getId(), view);
        sessions.revokeAll(account.getEmail());
        return view;
    }

    @Transactional
    public void resetPassword(long id, String temporaryPassword, long actorId) {
        if (temporaryPassword == null || temporaryPassword.length() < 12)
            throw ApiException.badRequest("WEAK_PASSWORD", "Mật khẩu tạm phải có ít nhất 12 ký tự");
        UserAccountEntity account = accounts.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        account.changePassword(encoder.encode(temporaryPassword), true);
        audit.record(actorId, "USER_PASSWORD_RESET", "USER_ACCOUNT", account.getId(), java.util.Map.of("mustChangePassword", true));
        sessions.revokeAll(account.getEmail());
    }

    private void validateEmployee(Long employeeId) {
        if (employeeId == null) return;
        boolean active = employees.findById(employeeId).map(employee -> employee.getStatus() == EmployeeStatus.ACTIVE).orElse(false);
        if (!active) throw ApiException.badRequest("EMPLOYEE_NOT_ACTIVE", "Chỉ có thể liên kết account với nhân viên đang hoạt động");
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
