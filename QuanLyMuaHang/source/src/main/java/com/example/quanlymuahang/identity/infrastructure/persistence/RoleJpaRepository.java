package com.example.quanlymuahang.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RoleJpaRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByCode(String code);
    List<RoleEntity> findAllByActiveTrueOrderByNameAsc();

    @Query(value = "SELECT id FROM roles WHERE code = :code FOR UPDATE", nativeQuery = true)
    Optional<Long> lockIdByCode(String code);
}
