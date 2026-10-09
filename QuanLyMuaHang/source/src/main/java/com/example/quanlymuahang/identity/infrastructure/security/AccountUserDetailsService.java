package com.example.quanlymuahang.identity.infrastructure.security;

import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountJpaRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountUserDetailsService implements UserDetailsService {
    private final UserAccountJpaRepository accounts;

    public AccountUserDetailsService(UserAccountJpaRepository accounts) { this.accounts = accounts; }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return accounts.findByEmail(com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountEntity.canonicalEmail(username))
                .map(AccountPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Tài khoản hoặc mật khẩu không đúng"));
    }
}
