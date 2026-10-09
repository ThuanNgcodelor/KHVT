package com.example.quanlymuahang.identity.web;

import com.example.quanlymuahang.identity.application.UserAdministrationService;
import com.example.quanlymuahang.identity.infrastructure.persistence.AccountStatus;
import com.example.quanlymuahang.identity.infrastructure.persistence.RoleJpaRepository;
import com.example.quanlymuahang.identity.infrastructure.security.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
public class UserAdministrationController {
    private final UserAdministrationService service;
    private final RoleJpaRepository roles;

    public UserAdministrationController(UserAdministrationService service, RoleJpaRepository roles) { this.service = service; this.roles = roles; }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('*') or hasAuthority('USER_READ')")
    public List<RoleView> roles() {
        return roles.findAllByActiveTrueOrderByNameAsc().stream().map(role -> new RoleView(role.getCode(), role.getName(),
                role.getDescription(), role.getPermissions().stream().map(permission -> permission.getCode()).sorted().toList())).toList();
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('*') or hasAuthority('USER_READ')")
    public Page<UserAdministrationService.UserView> users(@PageableDefault(size = 25, sort = "createdAt") Pageable pageable) {
        return service.list(pageable);
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('*') or hasAuthority('USER_MANAGE')")
    public UserAdministrationService.UserView create(@Valid @RequestBody CreateUserRequest body) {
        return service.create(new UserAdministrationService.CreateUser(body.email(), body.displayName(), body.initialPassword(), body.employeeId(), body.roleCodes()));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('*') or hasAuthority('USER_MANAGE')")
    public UserAdministrationService.UserView update(@PathVariable long id, @Valid @RequestBody UpdateUserRequest body, Authentication authentication) {
        return service.update(id, new UserAdministrationService.UpdateUser(body.displayName(), body.employeeId(), body.status(), body.roleCodes()),
                ((AccountPrincipal) authentication.getPrincipal()).id());
    }

    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasAuthority('*') or hasAuthority('USER_MANAGE')")
    public void resetPassword(@PathVariable long id, @Valid @RequestBody ResetPasswordRequest body) { service.resetPassword(id, body.temporaryPassword()); }

    public record RoleView(String code, String name, String description, List<String> permissions) {}
    public record CreateUserRequest(@NotBlank @Email String email, @NotBlank @Size(max = 255) String displayName,
                                    @NotBlank @Size(min = 12, max = 200) String initialPassword, Long employeeId,
                                    @NotEmpty Set<String> roleCodes) {}
    public record UpdateUserRequest(@NotBlank @Size(max = 255) String displayName, Long employeeId,
                                    AccountStatus status, @NotEmpty Set<String> roleCodes) {
        public UpdateUserRequest { if (status == null) status = AccountStatus.ACTIVE; }
    }
    public record ResetPasswordRequest(@NotBlank @Size(min = 12, max = 200) String temporaryPassword) {}
}
