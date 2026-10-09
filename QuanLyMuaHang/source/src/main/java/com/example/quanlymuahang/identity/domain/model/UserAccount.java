package com.example.quanlymuahang.identity.domain.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Domain model only; persistence mapping belongs in identity.infrastructure. */
public final class UserAccount {
    private final Long id;
    private final String username;
    private Long employeeId;
    private boolean active;
    private boolean mustChangePassword;
    private final Set<RoleCode> roles = EnumSet.noneOf(RoleCode.class);

    public UserAccount(Long id, String username, boolean active, boolean mustChangePassword) {
        this.id = id;
        this.username = Objects.requireNonNull(username, "username");
        this.active = active;
        this.mustChangePassword = mustChangePassword;
    }

    public boolean can(String permission) {
        if (roles.contains(RoleCode.ADMIN)) return true;
        return switch (permission) {
            case "PO_READ", "PRICE_READ" -> roles.contains(RoleCode.VIEWER) || roles.contains(RoleCode.PLANNER) || roles.contains(RoleCode.HR_MANAGER);
            case "PO_CREATE", "PO_EDIT", "IMPORT_RUN", "CATALOG_EDIT" -> roles.contains(RoleCode.PLANNER);
            case "PERSONNEL_MANAGE", "USER_MANAGE" -> roles.contains(RoleCode.HR_MANAGER);
            default -> false;
        };
    }

    public void assignRole(RoleCode role) { roles.add(Objects.requireNonNull(role)); }
    public void linkEmployee(Long employeeId) { this.employeeId = employeeId; }
    public void deactivate() { this.active = false; }
    public Long id() { return id; }
    public String username() { return username; }
    public Long employeeId() { return employeeId; }
    public boolean active() { return active; }
    public boolean mustChangePassword() { return mustChangePassword; }
    public Set<RoleCode> roles() { return Set.copyOf(roles); }
}
