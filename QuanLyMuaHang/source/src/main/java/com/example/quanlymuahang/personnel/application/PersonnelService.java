package com.example.quanlymuahang.personnel.application;

import com.example.quanlymuahang.personnel.infrastructure.persistence.DepartmentEntity;
import com.example.quanlymuahang.personnel.infrastructure.persistence.DepartmentJpaRepository;
import com.example.quanlymuahang.personnel.infrastructure.persistence.EmployeeEntity;
import com.example.quanlymuahang.personnel.infrastructure.persistence.EmployeeJpaRepository;
import com.example.quanlymuahang.personnel.infrastructure.persistence.EmployeeStatus;
import com.example.quanlymuahang.personnel.infrastructure.persistence.PositionEntity;
import com.example.quanlymuahang.personnel.infrastructure.persistence.PositionJpaRepository;
import com.example.quanlymuahang.identity.infrastructure.persistence.AccountStatus;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountJpaRepository;
import com.example.quanlymuahang.identity.infrastructure.security.SessionRevocationService;
import com.example.quanlymuahang.sharedkernel.application.AuditRecorder;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class PersonnelService {
    private final EmployeeJpaRepository employees;
    private final DepartmentJpaRepository departments;
    private final PositionJpaRepository positions;
    private final UserAccountJpaRepository accounts;
    private final SessionRevocationService sessions;
    private final AuditRecorder audit;

    public PersonnelService(EmployeeJpaRepository employees, DepartmentJpaRepository departments, PositionJpaRepository positions,
                            UserAccountJpaRepository accounts, SessionRevocationService sessions, AuditRecorder audit) {
        this.employees = employees; this.departments = departments; this.positions = positions;
        this.accounts = accounts; this.sessions = sessions; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<EmployeeView> search(String query, EmployeeStatus status, Pageable pageable) {
        return employees.search(query == null ? null : query.trim(), status, pageable).map(EmployeeView::from);
    }

    @Transactional(readOnly = true)
    public EmployeeView getEmployee(long id) { return EmployeeView.from(employee(id)); }

    @Transactional
    public EmployeeView createEmployee(EmployeeCommand command, long actorId) {
        if (employees.existsByEmployeeCodeIgnoreCase(command.employeeCode().trim())) throw ApiException.conflict("EMPLOYEE_CODE_EXISTS", "Mã nhân viên đã tồn tại");
        String email = clean(command.email());
        if (email != null && employees.existsByEmailIgnoreCase(email)) throw ApiException.conflict("EMPLOYEE_EMAIL_EXISTS", "Email nhân viên đã tồn tại");
        EmployeeEntity employee = new EmployeeEntity(command.employeeCode(), command.fullName(), email, clean(command.phone()),
                department(command.departmentId()), position(command.positionId()), command.joinedAt());
        EmployeeView view = EmployeeView.from(employees.save(employee));
        audit.record(actorId, "EMPLOYEE_CREATED", "EMPLOYEE", view.id(), view);
        return view;
    }

    @Transactional
    public EmployeeView updateEmployee(long id, EmployeeCommand command, long actorId) {
        EmployeeEntity employee = employee(id);
        if (employees.existsByEmployeeCodeIgnoreCaseAndIdNot(command.employeeCode().trim(), id))
            throw ApiException.conflict("EMPLOYEE_CODE_EXISTS", "Mã nhân viên đã tồn tại");
        String email = clean(command.email());
        if (email != null && employees.existsByEmailIgnoreCase(email) && !email.equalsIgnoreCase(employee.getEmail()))
            throw ApiException.conflict("EMPLOYEE_EMAIL_EXISTS", "Email nhân viên đã tồn tại");
        employee.update(command.employeeCode(), command.fullName(), email, clean(command.phone()), department(command.departmentId()), position(command.positionId()), command.joinedAt());
        EmployeeView view = EmployeeView.from(employee);
        audit.record(actorId, "EMPLOYEE_UPDATED", "EMPLOYEE", id, view);
        return view;
    }

    @Transactional
    public EmployeeView deactivateEmployee(long id, long actorId) {
        EmployeeEntity employee = employee(id);
        employee.deactivate(LocalDate.now());
        accounts.findByEmployeeId(id).ifPresent(account -> {
            account.setStatus(AccountStatus.DISABLED);
            sessions.revokeAll(account.getEmail());
            audit.record(actorId, "ACCOUNT_DISABLED_EMPLOYEE_DEACTIVATED", "USER_ACCOUNT", account.getId(), java.util.Map.of("status", "DISABLED"));
        });
        EmployeeView view = EmployeeView.from(employee);
        audit.record(actorId, "EMPLOYEE_DEACTIVATED", "EMPLOYEE", id, view);
        return view;
    }

    @Transactional
    public EmployeeView activateEmployee(long id, long actorId) {
        EmployeeEntity employee = employee(id);
        employee.activate();
        EmployeeView view = EmployeeView.from(employee);
        audit.record(actorId, "EMPLOYEE_ACTIVATED", "EMPLOYEE", id, view);
        return view;
    }

    @Transactional(readOnly = true)
    public List<DepartmentView> departments() { return departments.findAllByOrderByNameAsc().stream().map(DepartmentView::from).toList(); }

    @Transactional
    public DepartmentView saveDepartment(Long id, DepartmentCommand command, long actorId) {
        DepartmentEntity entity = id == null ? new DepartmentEntity(clean(command.code()), command.name()) : departments.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phòng ban"));
        if (id != null) entity.update(clean(command.code()), command.name(), command.active());
        if (id != null && id.equals(command.parentId())) throw ApiException.badRequest("INVALID_DEPARTMENT_PARENT", "Phòng ban không thể là cha của chính nó");
        entity.setParent(command.parentId() == null ? null : departments.findById(command.parentId()).orElseThrow(() -> ApiException.notFound("Không tìm thấy phòng ban cha")));
        DepartmentView view = DepartmentView.from(departments.save(entity));
        audit.record(actorId, id == null ? "DEPARTMENT_CREATED" : "DEPARTMENT_UPDATED", "DEPARTMENT", view.id(), view);
        return view;
    }

    @Transactional(readOnly = true)
    public List<PositionView> positions() { return positions.findAllByOrderByNameAsc().stream().map(PositionView::from).toList(); }

    @Transactional
    public PositionView savePosition(Long id, PositionCommand command, long actorId) {
        PositionEntity entity = id == null ? new PositionEntity(clean(command.code()), command.name()) : positions.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy chức vụ"));
        if (id != null) entity.update(clean(command.code()), command.name(), command.active());
        PositionView view = PositionView.from(positions.save(entity));
        audit.record(actorId, id == null ? "POSITION_CREATED" : "POSITION_UPDATED", "POSITION", view.id(), view);
        return view;
    }

    private EmployeeEntity employee(long id) { return employees.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhân viên")); }
    private DepartmentEntity department(Long id) { return id == null ? null : departments.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phòng ban")); }
    private PositionEntity position(Long id) { return id == null ? null : positions.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy chức vụ")); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record EmployeeCommand(String employeeCode, String fullName, String email, String phone,
                                  Long departmentId, Long positionId, LocalDate joinedAt) {}
    public record DepartmentCommand(String code, String name, Long parentId, boolean active) {}
    public record PositionCommand(String code, String name, boolean active) {}
    public record EmployeeView(Long id, String employeeCode, String fullName, String email, String phone,
                               Long departmentId, String departmentName, Long positionId, String positionName,
                               EmployeeStatus status, LocalDate joinedAt, LocalDate leftAt, long version) {
        static EmployeeView from(EmployeeEntity entity) {
            return new EmployeeView(entity.getId(), entity.getEmployeeCode(), entity.getFullName(), entity.getEmail(), entity.getPhone(),
                    entity.getDepartment() == null ? null : entity.getDepartment().getId(), entity.getDepartment() == null ? null : entity.getDepartment().getName(),
                    entity.getPosition() == null ? null : entity.getPosition().getId(), entity.getPosition() == null ? null : entity.getPosition().getName(),
                    entity.getStatus(), entity.getJoinedAt(), entity.getLeftAt(), entity.getVersion());
        }
    }
    public record DepartmentView(Long id, String code, String name, Long parentId, boolean active) {
        static DepartmentView from(DepartmentEntity entity) { return new DepartmentView(entity.getId(), entity.getCode(), entity.getName(), entity.getParent() == null ? null : entity.getParent().getId(), entity.isActive()); }
    }
    public record PositionView(Long id, String code, String name, boolean active) {
        static PositionView from(PositionEntity entity) { return new PositionView(entity.getId(), entity.getCode(), entity.getName(), entity.isActive()); }
    }
}
