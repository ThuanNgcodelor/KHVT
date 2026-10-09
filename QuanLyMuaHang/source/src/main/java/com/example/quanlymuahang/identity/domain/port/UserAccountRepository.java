package com.example.quanlymuahang.identity.domain.port;

import com.example.quanlymuahang.identity.domain.model.UserAccount;

import java.util.Optional;

public interface UserAccountRepository {
    Optional<UserAccount> findByUsername(String username);
    UserAccount save(UserAccount account);
}
