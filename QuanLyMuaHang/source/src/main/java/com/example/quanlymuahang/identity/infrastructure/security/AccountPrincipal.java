package com.example.quanlymuahang.identity.infrastructure.security;

import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AccountPrincipal implements UserDetails, Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private final Long id;
    private final String email;
    private final String displayName;
    private final String passwordHash;
    private final Long employeeId;
    private final boolean enabled;
    private final boolean locked;
    private final boolean mustChangePassword;
    private final Set<String> roles;
    private final Set<GrantedAuthority> authorities;

    public AccountPrincipal(UserAccountEntity account) {
        this(account, true);
    }

    public AccountPrincipal(UserAccountEntity account, boolean linkedEmployeeActive) {
        this.id = account.getId();
        this.email = account.getEmail();
        this.displayName = account.getDisplayName();
        this.passwordHash = account.getPasswordHash();
        this.employeeId = account.getEmployeeId();
        this.enabled = account.getStatus().name().equals("ACTIVE") && linkedEmployeeActive;
        this.locked = account.isLockedNow();
        this.mustChangePassword = account.isMustChangePassword();
        this.roles = account.getRoles().stream().map(role -> role.getCode()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<GrantedAuthority> result = new LinkedHashSet<>();
        account.getRoles().stream().filter(role -> role.isActive()).forEach(role -> {
            result.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
            role.getPermissions().forEach(permission -> result.add(new SimpleGrantedAuthority(permission.getCode())));
        });
        this.authorities = Set.copyOf(result);
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return !locked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
    public Long id() { return id; }
    public String email() { return email; }
    public String displayName() { return displayName; }
    public Long employeeId() { return employeeId; }
    public boolean mustChangePassword() { return mustChangePassword; }
    public Set<String> roles() { return roles; }
}
