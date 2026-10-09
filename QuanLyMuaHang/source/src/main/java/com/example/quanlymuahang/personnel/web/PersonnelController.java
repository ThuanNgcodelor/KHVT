package com.example.quanlymuahang.personnel.web;

import com.example.quanlymuahang.personnel.application.PersonnelService;
import com.example.quanlymuahang.personnel.infrastructure.persistence.EmployeeStatus;
import com.example.quanlymuahang.identity.infrastructure.security.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/personnel")
@PreAuthorize("hasAuthority('*') or hasAuthority('PERSONNEL_READ')")
public class PersonnelController {
    private final PersonnelService service;

    public PersonnelController(PersonnelService service) { this.service = service; }

    @GetMapping("/employees")
    public Page<PersonnelService.EmployeeView> employees(@RequestParam(required = false) String q,
                                                          @RequestParam(required = false) EmployeeStatus status,
                                                          @PageableDefault(size = 25, sort = "fullName") Pageable pageable) {
        return service.search(q, status, pageable);
    }
    @GetMapping("/employees/{id}") public PersonnelService.EmployeeView employee(@PathVariable long id) { return service.getEmployee(id); }

    @PostMapping("/employees")
    @PreAuthorize("hasAuthority('*') or hasAuthority('PERSONNEL_MANAGE')")
    public PersonnelService.EmployeeView create(@Valid @RequestBody EmployeeRequest body, Authentication authentication) { return service.createEmployee(body.command(), actor(authentication)); }

    @PutMapping("/employees/{id}")
    @PreAuthorize("hasAuthority('*') or hasAuthority('PERSONNEL_MANAGE')")
    public PersonnelService.EmployeeView update(@PathVariable long id, @Valid @RequestBody EmployeeRequest body, Authentication authentication) { return service.updateEmployee(id, body.command(), actor(authentication)); }

    @PostMapping("/employees/{id}/deactivate")
    @PreAuthorize("hasAuthority('*') or hasAuthority('PERSONNEL_MANAGE')")
    public PersonnelService.EmployeeView deactivate(@PathVariable long id, Authentication authentication) { return service.deactivateEmployee(id, actor(authentication)); }

    @PostMapping("/employees/{id}/activate")
    @PreAuthorize("hasAuthority('*') or hasAuthority('PERSONNEL_MANAGE')")
    public PersonnelService.EmployeeView activate(@PathVariable long id, Authentication authentication) { return service.activateEmployee(id, actor(authentication)); }

    @GetMapping("/departments") public List<PersonnelService.DepartmentView> departments() { return service.departments(); }
    @PostMapping("/departments")
    @PreAuthorize("hasAuthority('*') or hasAuthority('PERSONNEL_MANAGE')")
    public PersonnelService.DepartmentView createDepartment(@Valid @RequestBody DepartmentRequest body, Authentication authentication) { return service.saveDepartment(null, body.command(), actor(authentication)); }
    @PutMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('*') or hasAuthority('PERSONNEL_MANAGE')")
    public PersonnelService.DepartmentView updateDepartment(@PathVariable long id, @Valid @RequestBody DepartmentRequest body, Authentication authentication) { return service.saveDepartment(id, body.command(), actor(authentication)); }

    @GetMapping("/positions") public List<PersonnelService.PositionView> positions() { return service.positions(); }
    @PostMapping("/positions")
    @PreAuthorize("hasAuthority('*') or hasAuthority('PERSONNEL_MANAGE')")
    public PersonnelService.PositionView createPosition(@Valid @RequestBody PositionRequest body, Authentication authentication) { return service.savePosition(null, body.command(), actor(authentication)); }
    @PutMapping("/positions/{id}")
    @PreAuthorize("hasAuthority('*') or hasAuthority('PERSONNEL_MANAGE')")
    public PersonnelService.PositionView updatePosition(@PathVariable long id, @Valid @RequestBody PositionRequest body, Authentication authentication) { return service.savePosition(id, body.command(), actor(authentication)); }

    private static long actor(Authentication authentication) { return ((AccountPrincipal) authentication.getPrincipal()).id(); }

    public record EmployeeRequest(@NotBlank @Size(max = 80) String employeeCode, @NotBlank @Size(max = 255) String fullName,
                                  String email, String phone, Long departmentId, Long positionId,
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinedAt) {
        PersonnelService.EmployeeCommand command() { return new PersonnelService.EmployeeCommand(employeeCode, fullName, email, phone, departmentId, positionId, joinedAt); }
    }
    public record DepartmentRequest(String code, @NotBlank @Size(max = 255) String name, Long parentId, boolean active) {
        PersonnelService.DepartmentCommand command() { return new PersonnelService.DepartmentCommand(code, name, parentId, active); }
    }
    public record PositionRequest(String code, @NotBlank @Size(max = 255) String name, boolean active) {
        PersonnelService.PositionCommand command() { return new PersonnelService.PositionCommand(code, name, active); }
    }
}
