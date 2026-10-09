package com.example.quanlymuahang.identity.infrastructure.security;

import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountJpaRepository;
import com.example.quanlymuahang.personnel.infrastructure.persistence.EmployeeJpaRepository;
import com.example.quanlymuahang.personnel.infrastructure.persistence.EmployeeStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountUserDetailsService implements UserDetailsService {
    private final UserAccountJpaRepository accounts;
    private final EmployeeJpaRepository employees;

    public AccountUserDetailsService(UserAccountJpaRepository accounts, EmployeeJpaRepository employees) { this.accounts = accounts; this.employees = employees; }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return accounts.findByEmail(com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountEntity.canonicalEmail(username))
                .map(account -> new AccountPrincipal(account, account.getEmployeeId() == null || employees.findById(account.getEmployeeId())
                        .map(employee -> employee.getStatus() == EmployeeStatus.ACTIVE).orElse(false)))
                .orElseThrow(() -> new UsernameNotFoundException("Tài khoản hoặc mật khẩu không đúng"));
    }
}
