package com.example.quanlymuahang.identity.application;

import com.example.quanlymuahang.identity.infrastructure.persistence.AccountStatus;
import com.example.quanlymuahang.identity.infrastructure.persistence.RoleEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.RoleJpaRepository;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountJpaRepository;
import com.example.quanlymuahang.personnel.infrastructure.persistence.DepartmentEntity;
import com.example.quanlymuahang.personnel.infrastructure.persistence.DepartmentJpaRepository;
import com.example.quanlymuahang.personnel.infrastructure.persistence.EmployeeEntity;
import com.example.quanlymuahang.personnel.infrastructure.persistence.EmployeeJpaRepository;
import com.example.quanlymuahang.personnel.infrastructure.persistence.EmployeeStatus;
import com.example.quanlymuahang.personnel.infrastructure.persistence.PositionEntity;
import com.example.quanlymuahang.personnel.infrastructure.persistence.PositionJpaRepository;
import com.example.quanlymuahang.identity.infrastructure.security.SessionRevocationService;
import com.example.quanlymuahang.sharedkernel.application.AuditRecorder;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserAdministrationService {
    private final UserAccountJpaRepository accounts;
    private final RoleJpaRepository roles;
    private final PasswordEncoder encoder;
    private final EmployeeJpaRepository employees;
    private final DepartmentJpaRepository departments;
    private final PositionJpaRepository positions;
    private final SessionRevocationService sessions;
    private final AuditRecorder audit;

    public UserAdministrationService(UserAccountJpaRepository accounts, RoleJpaRepository roles, PasswordEncoder encoder,
                                     EmployeeJpaRepository employees, DepartmentJpaRepository departments,
                                     PositionJpaRepository positions, SessionRevocationService sessions, AuditRecorder audit) {
        this.accounts = accounts; this.roles = roles; this.encoder = encoder; this.employees = employees;
        this.departments = departments; this.positions = positions; this.sessions = sessions; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<UserView> list(Pageable pageable) { return list(null, null, null, pageable); }

    @Transactional(readOnly = true)
    public Page<UserView> list(String query, AccountStatus status, Long departmentId, Pageable pageable) {
        String search = clean(query);
        if (search != null && search.length() > 255)
            throw ApiException.badRequest("INVALID_SEARCH", "Từ khóa tìm kiếm tối đa 255 ký tự");
        Page<UserAccountEntity> page = accounts.findAll((root, criteria, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            if (status != null) filters.add(cb.equal(root.get("status"), status));
            if (departmentId != null) {
                var employee = criteria.subquery(Long.class);
                var profile = employee.from(EmployeeEntity.class);
                employee.select(profile.get("id")).where(cb.equal(profile.get("id"), root.get("employeeId")),
                        cb.equal(profile.get("department").get("id"), departmentId));
                filters.add(cb.exists(employee));
            }
            if (search != null) {
                String pattern = "%" + search.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                var employee = criteria.subquery(Long.class);
                var profile = employee.from(EmployeeEntity.class);
                employee.select(profile.get("id")).where(cb.equal(profile.get("id"), root.get("employeeId")),
                        cb.or(cb.like(cb.lower(profile.get("employeeCode")), pattern, '\\'), cb.like(cb.lower(profile.get("fullName")), pattern, '\\')));
                filters.add(cb.or(cb.like(cb.lower(root.get("email")), pattern, '\\'),
                        cb.like(cb.lower(root.get("displayName")), pattern, '\\'), cb.exists(employee)));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        }, accountPage(pageable));
        Set<Long> employeeIds = page.getContent().stream().map(UserAccountEntity::getEmployeeId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, EmployeeEntity> profiles = employees.findAllById(employeeIds).stream().collect(Collectors.toMap(EmployeeEntity::getId, Function.identity()));
        return page.map(account -> view(account, profiles.get(account.getEmployeeId())));
    }

    @Transactional(readOnly = true)
    public UserOptions options() {
        return new UserOptions(departments.findAllByOrderByNameAsc().stream()
                .map(item -> new DirectoryOption(item.getId(), item.getCode(), item.getName(), item.isActive())).toList(),
                positions.findAllByOrderByNameAsc().stream()
                        .map(item -> new DirectoryOption(item.getId(), item.getCode(), item.getName(), item.isActive())).toList());
    }

    @Transactional(readOnly = true)
    public Page<EmployeeOption> employeeOptions(String query, Long employeeId, Pageable pageable) {
        return employees.accountCandidates(clean(query), employeeId,
                PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), Sort.by("fullName").ascending().and(Sort.by("id"))))
                .map(EmployeeOption::from);
    }

    @Transactional(readOnly = true)
    public boolean isRoleAssignable(RoleEntity role, long actorId) {
        Set<String> permissions = effectivePermissions(actor(actorId));
        return (permissions.contains("*") || permissions.contains("USER_MANAGE")) && mayGrant(rolePermissions(Set.of(role)), permissions);
    }

    @Transactional
    public UserView create(CreateUser command, long actorId) {
        if (command == null || command.email() == null || command.email().isBlank()
                || command.displayName() == null || command.displayName().isBlank()
                || command.initialPassword() == null || command.roleCodes() == null)
            throw ApiException.badRequest("INVALID_USER", "Email, tên hiển thị, mật khẩu tạm và vai trò là bắt buộc");
        Set<RoleEntity> nextRoles = resolveRoles(command.roleCodes());
        checkAssignment(nextRoles, actorId);
        String email = UserAccountEntity.canonicalEmail(command.email());
        if (accounts.existsByEmail(email)) throw ApiException.conflict("EMAIL_EXISTS", "Email đã được sử dụng");
        if (command.initialPassword().length() < 12) throw ApiException.badRequest("WEAK_PASSWORD", "Mật khẩu tạm phải có ít nhất 12 ký tự");
        EmployeeEntity employee = resolveEmployee(command.employeeId(), command.employeeProfile(), command.displayName(), email, null, actorId);
        UserAccountEntity account = new UserAccountEntity(email, command.displayName(), encoder.encode(command.initialPassword()));
        account.changePassword(account.getPasswordHash(), true);
        account.setEmployeeId(employee == null ? null : employee.getId());
        account.replaceRoles(nextRoles);
        UserAccountEntity saved = accounts.saveAndFlush(account);
        UserView view = view(saved, employee);
        audit.record(actorId, "USER_CREATED", "USER_ACCOUNT", saved.getId(), view);
        return view;
    }

    @Transactional
    public UserView update(long id, UpdateUser command, long actorId) {
        UserAccountEntity account = accounts.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        checkTarget(account, actorId);
        if (command == null || clean(command.displayName()) == null || command.status() == null)
            throw ApiException.badRequest("INVALID_USER", "Tên hiển thị và trạng thái là bắt buộc");
        if (id == actorId && command.status() != AccountStatus.ACTIVE)
            throw ApiException.badRequest("CANNOT_DISABLE_SELF", "Bạn không thể tự khóa hoặc vô hiệu hóa tài khoản của mình");
        Set<RoleEntity> nextRoles = resolveRoles(command.roleCodes());
        checkAssignment(nextRoles, actorId);
        boolean removesAdmin = account.getRoles().stream().anyMatch(role -> role.getCode().equals("ADMIN"))
                && nextRoles.stream().noneMatch(role -> role.getCode().equals("ADMIN"));
        boolean willBeInactive = command.status() != AccountStatus.ACTIVE;
        boolean isAdmin = account.getRoles().stream().anyMatch(role -> role.getCode().equals("ADMIN"));
        if (account.getStatus() == AccountStatus.ACTIVE && (removesAdmin || willBeInactive) && isAdmin) {
            roles.lockIdByCode("ADMIN").orElseThrow(() -> new IllegalStateException("Role ADMIN chưa được khởi tạo"));
            if (accounts.lockActiveAdministrators("ADMIN").size() <= 1)
                throw ApiException.badRequest("LAST_ADMIN", "Không thể gỡ hoặc khóa quản trị viên hoạt động cuối cùng");
        }
        EmployeeEntity employee = resolveEmployee(command.employeeId(), command.employeeProfile(), command.displayName(), account.getEmail(), account.getId(), actorId);
        account.setDisplayName(command.displayName());
        account.setStatus(command.status());
        account.setEmployeeId(employee == null ? null : employee.getId());
        account.replaceRoles(nextRoles);
        UserView view = view(account, employee);
        audit.record(actorId, "USER_UPDATED", "USER_ACCOUNT", account.getId(), view);
        sessions.revokeAll(account.getEmail());
        return view;
    }

    @Transactional
    public void resetPassword(long id, String temporaryPassword, long actorId) {
        if (temporaryPassword == null || temporaryPassword.length() < 12)
            throw ApiException.badRequest("WEAK_PASSWORD", "Mật khẩu tạm phải có ít nhất 12 ký tự");
        UserAccountEntity account = accounts.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        checkTarget(account, actorId);
        account.changePassword(encoder.encode(temporaryPassword), true);
        audit.record(actorId, "USER_PASSWORD_RESET", "USER_ACCOUNT", account.getId(), Map.of("mustChangePassword", true));
        sessions.revokeAll(account.getEmail());
    }

    private EmployeeEntity resolveEmployee(Long employeeId, EmployeeProfile profile, String fullName, String accountEmail, Long accountId, long actorId) {
        EmployeeEntity employee = null;
        if (employeeId != null) {
            employee = employees.findById(employeeId).orElseThrow(() -> ApiException.badRequest("EMPLOYEE_NOT_ACTIVE", "Chỉ có thể liên kết tài khoản với nhân viên đang hoạt động"));
            if (employee.getStatus() != EmployeeStatus.ACTIVE)
                throw ApiException.badRequest("EMPLOYEE_NOT_ACTIVE", "Chỉ có thể liên kết tài khoản với nhân viên đang hoạt động");
            if (accounts.findByEmployeeId(employeeId).filter(linked -> !linked.getId().equals(accountId)).isPresent())
                throw ApiException.conflict("EMPLOYEE_ALREADY_LINKED", "Hồ sơ này đã liên kết với một tài khoản khác");
        }
        if (profile == null || (employee == null && clean(profile.employeeCode()) == null && profile.departmentId() == null && profile.positionId() == null)) return employee;
        String code = clean(profile.employeeCode());
        if (code == null) code = employee == null ? "NV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT) : employee.getEmployeeCode();
        if (code.length() > 80) throw ApiException.badRequest("INVALID_EMPLOYEE_CODE", "Mã nhân viên tối đa 80 ký tự");
        boolean duplicateCode = employee == null ? employees.existsByEmployeeCodeIgnoreCase(code) : employees.existsByEmployeeCodeIgnoreCaseAndIdNot(code, employee.getId());
        if (duplicateCode) throw ApiException.conflict("EMPLOYEE_CODE_EXISTS", "Mã nhân viên đã tồn tại");
        boolean currentProfile = accountId != null && employeeId != null && accounts.findById(accountId)
                .filter(account -> employeeId.equals(account.getEmployeeId())).isPresent();
        DepartmentEntity previousDepartment = employee == null ? null : employee.getDepartment();
        PositionEntity previousPosition = employee == null ? null : employee.getPosition();
        DepartmentEntity department = profile.departmentId() == null ? null : departments.findById(profile.departmentId())
                .filter(item -> item.isActive() || (currentProfile && previousDepartment != null && item.getId().equals(previousDepartment.getId())))
                .orElseThrow(() -> ApiException.badRequest("DEPARTMENT_NOT_ACTIVE", "Phòng ban không tồn tại hoặc đã ngừng sử dụng"));
        PositionEntity position = profile.positionId() == null ? null : positions.findById(profile.positionId())
                .filter(item -> item.isActive() || (currentProfile && previousPosition != null && item.getId().equals(previousPosition.getId())))
                .orElseThrow(() -> ApiException.badRequest("POSITION_NOT_ACTIVE", "Chức vụ không tồn tại hoặc đã ngừng sử dụng"));
        if (employee == null) {
            if (employees.existsByEmailIgnoreCase(accountEmail)) throw ApiException.conflict("EMPLOYEE_EMAIL_EXISTS", "Đã có hồ sơ dùng email này; hãy liên kết hồ sơ hiện có");
            employee = employees.saveAndFlush(new EmployeeEntity(code, fullName, accountEmail, null, department, position, null));
            audit.record(actorId, "EMPLOYEE_CREATED_WITH_ACCOUNT", "EMPLOYEE", employee.getId(), Map.of("employeeCode", code, "fullName", fullName));
        } else {
            employee.update(code, fullName, employee.getEmail(), employee.getPhone(), department, position, employee.getJoinedAt());
            audit.record(actorId, "EMPLOYEE_UPDATED_WITH_ACCOUNT", "EMPLOYEE", employee.getId(), Map.of("employeeCode", code, "fullName", fullName));
        }
        return employee;
    }

    private UserAccountEntity actor(long actorId) {
        UserAccountEntity actor = accounts.findById(actorId).filter(item -> item.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> forbidden("ACCOUNT_MANAGEMENT_FORBIDDEN", "Tài khoản quản trị không còn hoạt động"));
        if (actor.getEmployeeId() != null && employees.findById(actor.getEmployeeId()).filter(item -> item.getStatus() == EmployeeStatus.ACTIVE).isEmpty())
            throw forbidden("ACCOUNT_MANAGEMENT_FORBIDDEN", "Hồ sơ quản trị không còn hoạt động");
        return actor;
    }

    private void checkTarget(UserAccountEntity target, long actorId) {
        Set<String> permissions = effectivePermissions(actor(actorId));
        if (!permissions.contains("*") && (!permissions.contains("USER_MANAGE") || !mayGrant(rolePermissions(target.getRoles()), permissions)))
            throw forbidden("ACCOUNT_MANAGEMENT_FORBIDDEN", "Bạn không thể sửa hoặc reset mật khẩu tài khoản có quyền vượt phạm vi được giao");
    }

    private void checkAssignment(Set<RoleEntity> nextRoles, long actorId) {
        Set<String> permissions = effectivePermissions(actor(actorId));
        if (!permissions.contains("*") && (!permissions.contains("USER_MANAGE") || !mayGrant(rolePermissions(nextRoles), permissions)))
            throw forbidden("ROLE_ASSIGNMENT_FORBIDDEN", "Bạn chỉ được cấp vai trò trong phạm vi quyền của mình");
    }

    private static Set<String> effectivePermissions(UserAccountEntity account) {
        return rolePermissions(account.getRoles().stream().filter(RoleEntity::isActive).collect(Collectors.toSet()));
    }

    private static Set<String> rolePermissions(Set<RoleEntity> roles) {
        return roles.stream().flatMap(role -> role.getPermissions().stream()).map(permission -> permission.getCode()).collect(Collectors.toSet());
    }

    private static boolean mayGrant(Set<String> requested, Set<String> actor) { return actor.contains("*") || (!requested.contains("*") && actor.containsAll(requested)); }
    private static ApiException forbidden(String code, String message) { return new ApiException(HttpStatus.FORBIDDEN, code, message); }

    private Set<RoleEntity> resolveRoles(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) throw ApiException.badRequest("ROLE_REQUIRED", "Tài khoản phải có ít nhất một vai trò");
        if (roleCodes.stream().anyMatch(code -> clean(code) == null)) throw ApiException.badRequest("UNKNOWN_ROLE", "Vai trò không hợp lệ");
        Set<String> codes = roleCodes.stream().map(code -> code.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        List<RoleEntity> found = roles.findAllByActiveTrueOrderByNameAsc().stream().filter(role -> codes.contains(role.getCode())).toList();
        if (found.size() != codes.size()) throw ApiException.badRequest("UNKNOWN_ROLE", "Một hoặc nhiều vai trò không tồn tại hoặc đã bị ngừng");
        return new HashSet<>(found);
    }

    private static Pageable accountPage(Pageable pageable) {
        Set<String> allowed = Set.of("id", "createdAt", "email", "displayName", "status", "lastLoginAt");
        List<Sort.Order> order = pageable.getSort().stream().filter(item -> allowed.contains(item.getProperty())).toList();
        Sort sort = order.isEmpty() ? Sort.by(Sort.Direction.DESC, "createdAt") : Sort.by(order);
        if (order.stream().noneMatch(item -> item.getProperty().equals("id"))) sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), sort);
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static UserView view(UserAccountEntity account, EmployeeEntity employee) {
        return new UserView(account.getId(), account.getEmail(), account.getDisplayName(), account.getEmployeeId(),
                account.getStatus(), account.isMustChangePassword(), account.getFailedLoginCount(), account.getLockedUntil(), account.getLastLoginAt(),
                account.getRoles().stream().map(RoleEntity::getCode).collect(Collectors.toUnmodifiableSet()),
                employee == null ? null : employee.getEmployeeCode(), employee == null ? null : employee.getFullName(), employee == null ? null : employee.getStatus(),
                employee == null || employee.getDepartment() == null ? null : employee.getDepartment().getId(), employee == null || employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                employee == null || employee.getPosition() == null ? null : employee.getPosition().getId(), employee == null || employee.getPosition() == null ? null : employee.getPosition().getName());
    }

    public record EmployeeProfile(String employeeCode, Long departmentId, Long positionId) {}
    public record CreateUser(String email, String displayName, String initialPassword, Long employeeId, Set<String> roleCodes, EmployeeProfile employeeProfile) {
        public CreateUser(String email, String displayName, String initialPassword, Long employeeId, Set<String> roleCodes) { this(email, displayName, initialPassword, employeeId, roleCodes, null); }
    }
    public record UpdateUser(String displayName, Long employeeId, AccountStatus status, Set<String> roleCodes, EmployeeProfile employeeProfile) {
        public UpdateUser(String displayName, Long employeeId, AccountStatus status, Set<String> roleCodes) { this(displayName, employeeId, status, roleCodes, null); }
    }
    public record UserView(Long id, String email, String displayName, Long employeeId, AccountStatus status,
                           boolean mustChangePassword, int failedLoginCount, java.time.Instant lockedUntil,
                           java.time.Instant lastLoginAt, Set<String> roleCodes, String employeeCode, String employeeName,
                           EmployeeStatus employeeStatus, Long departmentId, String departmentName, Long positionId, String positionName) {}
    public record DirectoryOption(Long id, String code, String name, boolean active) {}
    public record UserOptions(List<DirectoryOption> departments, List<DirectoryOption> positions) {}
    public record EmployeeOption(Long id, String employeeCode, String fullName, Long departmentId, String departmentName,
                                 Long positionId, String positionName, EmployeeStatus status) {
        static EmployeeOption from(EmployeeEntity item) {
            return new EmployeeOption(item.getId(), item.getEmployeeCode(), item.getFullName(),
                    item.getDepartment() == null ? null : item.getDepartment().getId(), item.getDepartment() == null ? null : item.getDepartment().getName(),
                    item.getPosition() == null ? null : item.getPosition().getId(), item.getPosition() == null ? null : item.getPosition().getName(), item.getStatus());
        }
    }
}
