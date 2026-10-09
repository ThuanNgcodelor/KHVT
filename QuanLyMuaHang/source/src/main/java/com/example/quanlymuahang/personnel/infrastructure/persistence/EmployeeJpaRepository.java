package com.example.quanlymuahang.personnel.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmployeeJpaRepository extends JpaRepository<EmployeeEntity, Long> {
    Optional<EmployeeEntity> findByEmployeeCodeIgnoreCase(String employeeCode);
    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);
    boolean existsByEmailIgnoreCase(String email);
    @Query("select e from EmployeeEntity e where (:query is null or :query = '' or lower(e.employeeCode) like lower(concat('%', :query, '%')) or lower(e.fullName) like lower(concat('%', :query, '%')) or lower(e.email) like lower(concat('%', :query, '%'))) and (:status is null or e.status = :status)")
    Page<EmployeeEntity> search(@Param("query") String query, @Param("status") EmployeeStatus status, Pageable pageable);
}
