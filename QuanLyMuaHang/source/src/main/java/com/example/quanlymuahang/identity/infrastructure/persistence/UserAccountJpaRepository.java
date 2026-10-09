package com.example.quanlymuahang.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.List;

public interface UserAccountJpaRepository extends JpaRepository<UserAccountEntity, Long> {
    Optional<UserAccountEntity> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<UserAccountEntity> findByEmployeeId(Long employeeId);
    Page<UserAccountEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    @Query("select count(distinct a) from UserAccountEntity a join a.roles r where r.code = :role and a.status = com.example.quanlymuahang.identity.infrastructure.persistence.AccountStatus.ACTIVE")
    long countActiveAdministrators(@Param("role") String role);

    @Query(value = "SELECT ua.id FROM user_accounts ua JOIN user_roles ur ON ur.user_id = ua.id " +
            "JOIN roles r ON r.id = ur.role_id WHERE r.code = :role AND ua.status = 'ACTIVE' FOR UPDATE", nativeQuery = true)
    List<Long> lockActiveAdministrators(@Param("role") String role);
}
