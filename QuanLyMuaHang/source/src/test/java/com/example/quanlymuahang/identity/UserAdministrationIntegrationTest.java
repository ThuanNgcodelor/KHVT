package com.example.quanlymuahang.identity;

import com.example.quanlymuahang.identity.application.UserAdministrationService;
import com.example.quanlymuahang.identity.application.UserAdministrationService.CreateUser;
import com.example.quanlymuahang.identity.application.UserAdministrationService.EmployeeProfile;
import com.example.quanlymuahang.identity.application.UserAdministrationService.UpdateUser;
import com.example.quanlymuahang.identity.infrastructure.persistence.AccountStatus;
import com.example.quanlymuahang.identity.infrastructure.persistence.RoleJpaRepository;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountJpaRepository;
import com.example.quanlymuahang.identity.infrastructure.security.AccountPrincipal;
import com.example.quanlymuahang.identity.infrastructure.security.SessionRevocationService;
import com.example.quanlymuahang.personnel.infrastructure.persistence.DepartmentEntity;
import com.example.quanlymuahang.personnel.infrastructure.persistence.DepartmentJpaRepository;
import com.example.quanlymuahang.personnel.infrastructure.persistence.EmployeeEntity;
import com.example.quanlymuahang.personnel.infrastructure.persistence.EmployeeJpaRepository;
import com.example.quanlymuahang.personnel.infrastructure.persistence.PositionEntity;
import com.example.quanlymuahang.personnel.infrastructure.persistence.PositionJpaRepository;
import com.example.quanlymuahang.sharedkernel.application.AuditRecorder;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Every service call has its own committed transaction, so rollback checks do not
// rely on a surrounding test transaction. This uses H2/servlet sessions, not Redis.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_phase1;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "app.bootstrap-admin.email=", "app.bootstrap-admin.password=", "debug=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class UserAdministrationIntegrationTest {
    private static final String PASSWORD = "synthetic-phase1-password";
    @Autowired UserAdministrationService service;
    @Autowired UserAccountJpaRepository users;
    @Autowired EmployeeJpaRepository employees;
    @Autowired DepartmentJpaRepository departments;
    @Autowired PositionJpaRepository positions;
    @Autowired RoleJpaRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @SpyBean AuditRecorder audit;
    @SpyBean SessionRevocationService sessions;
    private long adminId;

    @BeforeEach
    void prepare() {
        reset(audit, sessions);
        jdbc.update("delete from audit_logs");
        jdbc.update("delete from user_roles");
        jdbc.update("delete from user_accounts");
        jdbc.update("delete from role_permissions");
        jdbc.update("delete from roles");
        jdbc.update("delete from permissions");
        jdbc.update("delete from employees");
        jdbc.update("delete from departments");
        jdbc.update("delete from positions");
        role("ADMIN", "*");
        role("VIEWER", "PO_READ");
        role("PLANNER", "PO_READ", "PO_CREATE");
        role("DELEGATE", "USER_READ", "USER_MANAGE", "PO_READ");
        role("DIRECTORY", "PERSONNEL_READ", "PERSONNEL_MANAGE");
        adminId = account("admin@example.test", "ADMIN").getId();
    }

    @Test
    void optionalAndBlankProfilesDoNotCreateEmployees() {
        var plain = service.create(create("plain@example.test", null, null), adminId);
        var blank = service.create(create("blank@example.test", null, new EmployeeProfile("  ", null, null)), adminId);
        assertThat(plain.employeeId()).isNull();
        assertThat(blank.employeeId()).isNull();
        assertThat(employees.count()).isZero();
        assertThat(plain.mustChangePassword()).isTrue();
        assertThat(encoder.matches(PASSWORD, users.findById(plain.id()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void inlineProfileCreatesAndUpdatesSameEmployeeWhilePreservingOtherFields() {
        DepartmentEntity department = departments.saveAndFlush(new DepartmentEntity("KHVT", "Kế hoạch vật tư"));
        PositionEntity position = positions.saveAndFlush(new PositionEntity("PP", "Phó phòng"));
        var created = service.create(create("inline@example.test", null, new EmployeeProfile(null, department.getId(), position.getId())), adminId);
        assertThat(created.employeeCode()).startsWith("NV-");
        assertThat(created.departmentName()).isEqualTo("Kế hoạch vật tư");
        assertThat(created.positionName()).isEqualTo("Phó phòng");
        EmployeeEntity employee = employees.findById(created.employeeId()).orElseThrow();
        employee.update(employee.getEmployeeCode(), employee.getFullName(), "profile@example.test", "0123456789", department, position, LocalDate.of(2020, 1, 2));
        employees.saveAndFlush(employee);
        var updated = service.update(created.id(), new UpdateUser("Tên mới", employee.getId(), AccountStatus.ACTIVE,
                Set.of("VIEWER"), new EmployeeProfile("KH-001", department.getId(), position.getId())), adminId);
        assertThat(updated.employeeId()).isEqualTo(created.employeeId());
        assertThat(updated.employeeName()).isEqualTo("Tên mới");
        assertThat(updated.employeeCode()).isEqualTo("KH-001");
        EmployeeEntity preserved = employees.findById(employee.getId()).orElseThrow();
        assertThat(preserved.getEmail()).isEqualTo("profile@example.test");
        assertThat(preserved.getPhone()).isEqualTo("0123456789");
        assertThat(preserved.getJoinedAt()).isEqualTo(LocalDate.of(2020, 1, 2));
        assertThat(employees.count()).isEqualTo(1);
        verify(sessions).revokeAll("inline@example.test");
    }

    @Test
    void editingCurrentProfileCanKeepInactiveDirectoryButCannotAssignAnotherInactiveEntry() {
        var department = departments.saveAndFlush(new DepartmentEntity("OLD-KH", "Phòng ban cũ"));
        var position = positions.saveAndFlush(new PositionEntity("OLD-PP", "Chức vụ cũ"));
        var created = service.create(create("old-directory@example.test", null,
                new EmployeeProfile("NV-OLD", department.getId(), position.getId())), adminId);
        department.update(department.getCode(), department.getName(), false);
        position.update(position.getCode(), position.getName(), false);
        departments.saveAndFlush(department);
        positions.saveAndFlush(position);
        var updated = service.update(created.id(), new UpdateUser("Tên đã đổi", created.employeeId(), AccountStatus.ACTIVE,
                Set.of("PLANNER"), new EmployeeProfile("NV-OLD", department.getId(), position.getId())), adminId);
        assertThat(updated.employeeName()).isEqualTo("Tên đã đổi");
        assertThat(updated.departmentId()).isEqualTo(department.getId());
        assertThat(updated.positionId()).isEqualTo(position.getId());
        assertThat(updated.roleCodes()).containsExactly("PLANNER");
        var otherDepartment = new DepartmentEntity("OTHER-KH", "Phòng ban ngừng khác");
        var otherPosition = new PositionEntity("OTHER-PP", "Chức vụ ngừng khác");
        otherDepartment.update(otherDepartment.getCode(), otherDepartment.getName(), false);
        otherPosition.update(otherPosition.getCode(), otherPosition.getName(), false);
        departments.saveAndFlush(otherDepartment);
        positions.saveAndFlush(otherPosition);
        expectCode(() -> service.update(created.id(), new UpdateUser("Không được lưu", created.employeeId(), AccountStatus.ACTIVE,
                Set.of("VIEWER"), new EmployeeProfile("NV-OLD", otherDepartment.getId(), position.getId())), adminId), "DEPARTMENT_NOT_ACTIVE");
        expectCode(() -> service.update(created.id(), new UpdateUser("Không được lưu", created.employeeId(), AccountStatus.ACTIVE,
                Set.of("VIEWER"), new EmployeeProfile("NV-OLD", department.getId(), otherPosition.getId())), adminId), "POSITION_NOT_ACTIVE");
        assertThat(users.findById(created.id()).orElseThrow().getDisplayName()).isEqualTo("Tên đã đổi");
    }

    @Test
    void blankProfileForExistingEmployeeClearsOptionalDirectoryAndSyncsNameWhileKeepingCode() {
        var department = departments.saveAndFlush(new DepartmentEntity("KH", "KHVT"));
        var position = positions.saveAndFlush(new PositionEntity("PP", "Phó phòng"));
        var created = service.create(create("clear-directory@example.test", null,
                new EmployeeProfile("KEEP-CODE", department.getId(), position.getId())), adminId);
        var updated = service.update(created.id(), new UpdateUser("Tên cập nhật", created.employeeId(), AccountStatus.ACTIVE,
                Set.of("VIEWER"), new EmployeeProfile("  ", null, null)), adminId);
        assertThat(updated.employeeId()).isEqualTo(created.employeeId());
        assertThat(updated.employeeCode()).isEqualTo("KEEP-CODE");
        assertThat(updated.employeeName()).isEqualTo("Tên cập nhật");
        assertThat(updated.departmentId()).isNull();
        assertThat(updated.positionId()).isNull();
        assertThat(employees.count()).isEqualTo(1);
    }

    @Test
    void validationRejectsDuplicateCodeAndInactiveDirectoryWithoutOrphans() {
        DepartmentEntity inactiveDepartment = new DepartmentEntity("OLD", "Ngừng");
        inactiveDepartment.update("OLD", "Ngừng", false);
        departments.saveAndFlush(inactiveDepartment);
        PositionEntity inactivePosition = new PositionEntity("OLD", "Ngừng");
        inactivePosition.update("OLD", "Ngừng", false);
        positions.saveAndFlush(inactivePosition);
        service.create(create("first@example.test", null, new EmployeeProfile("NV-01", null, null)), adminId);
        long beforeUsers = users.count(), beforeEmployees = employees.count();
        expectCode(() -> service.create(create("duplicate@example.test", null, new EmployeeProfile("nv-01", null, null)), adminId), "EMPLOYEE_CODE_EXISTS");
        expectCode(() -> service.create(create("department@example.test", null, new EmployeeProfile(null, inactiveDepartment.getId(), null)), adminId), "DEPARTMENT_NOT_ACTIVE");
        expectCode(() -> service.create(create("position@example.test", null, new EmployeeProfile(null, null, inactivePosition.getId())), adminId), "POSITION_NOT_ACTIVE");
        expectCode(() -> service.create(create("first@example.test", null, new EmployeeProfile("OTHER", null, null)), adminId), "EMAIL_EXISTS");
        assertThat(users.count()).isEqualTo(beforeUsers);
        assertThat(employees.count()).isEqualTo(beforeEmployees);
    }

    @Test
    void auditFailureRollsBackBothAccountAndNewEmployee() {
        long beforeAudit = jdbc.queryForObject("select count(*) from audit_logs", Long.class);
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit)
                .record(eq(adminId), eq("USER_CREATED"), eq("USER_ACCOUNT"), anyLong(), any());
        assertThatThrownBy(() -> service.create(create("rollback@example.test", null, new EmployeeProfile("ROLLBACK", null, null)), adminId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(users.findByEmail("rollback@example.test")).isEmpty();
        assertThat(employees.findByEmployeeCodeIgnoreCase("ROLLBACK")).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs", Long.class)).isEqualTo(beforeAudit);
    }

    @Test
    void existingEmployeeLinkIsPreservedAndCannotBeShared() {
        EmployeeEntity employee = employees.saveAndFlush(new EmployeeEntity("EXISTING", "Hồ sơ", null, null, null, null, null));
        var linked = service.create(create("linked@example.test", employee.getId(), null), adminId);
        assertThat(linked.employeeCode()).isEqualTo("EXISTING");
        expectCode(() -> service.create(create("second@example.test", employee.getId(), null), adminId), "EMPLOYEE_ALREADY_LINKED");
        var updated = service.update(linked.id(), new UpdateUser("Đổi tài khoản", employee.getId(), AccountStatus.ACTIVE, Set.of("VIEWER")), adminId);
        assertThat(updated.employeeName()).isEqualTo("Hồ sơ");
        assertThat(employees.count()).isEqualTo(1);
    }

    @Test
    void serverSearchFiltersWholeResultAndPagesWithStableCounts() {
        var khvt = departments.saveAndFlush(new DepartmentEntity("KH", "KHVT"));
        var sales = departments.saveAndFlush(new DepartmentEntity("BH", "Bán hàng"));
        var first = service.create(new CreateUser("first@example.test", "Nguyễn An", PASSWORD, null, Set.of("VIEWER"), new EmployeeProfile("KH-001", khvt.getId(), null)), adminId);
        var second = service.create(new CreateUser("second@example.test", "Nguyễn Bình", PASSWORD, null, Set.of("VIEWER"), new EmployeeProfile("BH-002", sales.getId(), null)), adminId);
        service.update(second.id(), new UpdateUser("Nguyễn Bình", second.employeeId(), AccountStatus.DISABLED, Set.of("VIEWER")), adminId);
        var page = service.list("nguyễn", null, null, PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(service.list("KH-001", null, null, PageRequest.of(0, 25)).getContent()).extracting("id").containsExactly(first.id());
        assertThat(service.list("FIRST@EXAMPLE", AccountStatus.ACTIVE, khvt.getId(), PageRequest.of(0, 25)).getTotalElements()).isEqualTo(1);
        assertThat(service.list(null, AccountStatus.DISABLED, sales.getId(), PageRequest.of(0, 25)).getContent()).extracting("id").containsExactly(second.id());
        assertThat(service.list("%", null, null, PageRequest.of(0, 25)).getTotalElements()).isZero();
    }

    @Test
    void delegatesCanGrantOnlyRolesWithinTheirOwnPermissions() {
        long delegate = account("delegate@example.test", "DELEGATE").getId();
        var viewer = service.create(create("viewer@example.test", null, null), delegate);
        expectCode(() -> service.create(new CreateUser("escalate@example.test", "Escalate", PASSWORD, null, Set.of("ADMIN")), delegate), "ROLE_ASSIGNMENT_FORBIDDEN");
        expectCode(() -> service.create(new CreateUser("outside@example.test", "Outside", PASSWORD, null, Set.of("PLANNER")), delegate), "ROLE_ASSIGNMENT_FORBIDDEN");
        expectCode(() -> service.update(viewer.id(), new UpdateUser("Escalate", null, AccountStatus.ACTIVE, Set.of("ADMIN")), delegate), "ROLE_ASSIGNMENT_FORBIDDEN");
        assertThat(users.findByEmail("escalate@example.test")).isEmpty();
        assertThat(users.findById(viewer.id()).orElseThrow().getRoles()).extracting("code").containsExactly("VIEWER");
    }

    @Test
    void delegatesCannotModifyOrResetHigherPrivilegeAccountsEvenWithMultipleAdmins() {
        long delegate = account("delegate@example.test", "DELEGATE").getId();
        var otherAdmin = account("other-admin@example.test", "ADMIN");
        String hash = otherAdmin.getPasswordHash();
        expectCode(() -> service.update(otherAdmin.getId(), new UpdateUser("Takeover", null, AccountStatus.ACTIVE, Set.of("VIEWER")), delegate), "ACCOUNT_MANAGEMENT_FORBIDDEN");
        expectCode(() -> service.resetPassword(otherAdmin.getId(), "synthetic-reset-password", delegate), "ACCOUNT_MANAGEMENT_FORBIDDEN");
        assertThat(users.findById(otherAdmin.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
        verify(sessions, never()).revokeAll("other-admin@example.test");
    }

    @Test
    void selfDisableAndLastAdminRemovalRemainBlocked() {
        expectCode(() -> service.update(adminId, new UpdateUser("Admin", null, AccountStatus.DISABLED, Set.of("ADMIN")), adminId), "CANNOT_DISABLE_SELF");
        expectCode(() -> service.update(adminId, new UpdateUser("Admin", null, AccountStatus.ACTIVE, Set.of("VIEWER")), adminId), "LAST_ADMIN");
        assertThat(users.findById(adminId).orElseThrow().getRoles()).extracting("code").containsExactly("ADMIN");
    }

    @Test
    void directoryOnlyUserCannotCreateOrResetAccountsThroughService() {
        long directory = account("directory@example.test", "DIRECTORY").getId();
        expectCode(() -> service.create(create("forbidden@example.test", null, null), directory), "ROLE_ASSIGNMENT_FORBIDDEN");
        expectCode(() -> service.resetPassword(adminId, "synthetic-reset-password", directory), "ACCOUNT_MANAGEMENT_FORBIDDEN");
    }

    @Test
    void optionsRequireUserReadAndReturnOnlyAccountDirectoryFields() throws Exception {
        var department = departments.saveAndFlush(new DepartmentEntity("KH", "KHVT"));
        positions.saveAndFlush(new PositionEntity("NV", "Nhân viên"));
        EmployeeEntity current = employees.saveAndFlush(new EmployeeEntity("CURRENT", "Current", null, "private-phone", department, null, null));
        var linked = service.create(create("linked@example.test", current.getId(), null), adminId);
        current.deactivate(LocalDate.now());
        employees.saveAndFlush(current);
        employees.saveAndFlush(new EmployeeEntity("AVAILABLE", "Available", null, null, null, null, null));
        var delegate = account("delegate@example.test", "DELEGATE");
        mvc.perform(get("/api/admin/users/options").servletPath("/api/admin/users/options").with(authentication(auth(delegate))))
                .andExpect(status().isOk()).andExpect(jsonPath("departments[0].name").value("KHVT"))
                .andExpect(jsonPath("positions[0].name").value("Nhân viên"));
        mvc.perform(get("/api/admin/users/employees").servletPath("/api/admin/users/employees").with(authentication(auth(delegate))))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].employeeCode").value("AVAILABLE"))
                .andExpect(jsonPath("content[0].phone").doesNotExist());
        mvc.perform(get("/api/admin/users/employees").servletPath("/api/admin/users/employees").param("employeeId", linked.employeeId().toString()).with(authentication(auth(delegate))))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2));
        mvc.perform(get("/api/admin/users/options").servletPath("/api/admin/users/options").with(authentication(auth(account("hr@example.test", "DIRECTORY")))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/roles").servletPath("/api/admin/roles").with(authentication(auth(delegate))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.code == 'ADMIN')].assignable").value(org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$[?(@.code == 'VIEWER')].assignable").value(org.hamcrest.Matchers.contains(true)));
    }

    private CreateUser create(String email, Long employeeId, EmployeeProfile profile) {
        return new CreateUser(email, "Fixture user", PASSWORD, employeeId, Set.of("VIEWER"), profile);
    }

    private void role(String code, String... permissions) {
        jdbc.update("insert into roles(code,name,system_role,active,created_at,updated_at) values (?,?,true,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", code, code);
        for (String permission : permissions) {
            if (jdbc.queryForObject("select count(*) from permissions where code=?", Long.class, permission) == 0)
                jdbc.update("insert into permissions(code,name,system_permission) values (?,?,true)", permission, permission);
            jdbc.update("insert into role_permissions(role_id,permission_id) select r.id,p.id from roles r,permissions p where r.code=? and p.code=?", code, permission);
        }
    }

    private UserAccountEntity account(String email, String role) {
        var account = new UserAccountEntity(email, email, encoder.encode(PASSWORD));
        account.replaceRoles(Set.of(roles.findByCode(role).orElseThrow()));
        return users.saveAndFlush(account);
    }

    private UsernamePasswordAuthenticationToken auth(UserAccountEntity account) {
        var principal = new AccountPrincipal(account);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static void expectCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        assertThatThrownBy(action).isInstanceOf(ApiException.class).satisfies(exception -> assertThat(((ApiException) exception).code()).isEqualTo(code));
    }
}
