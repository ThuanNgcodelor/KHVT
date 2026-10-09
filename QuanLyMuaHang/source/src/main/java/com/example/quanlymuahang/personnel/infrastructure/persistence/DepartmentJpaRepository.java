package com.example.quanlymuahang.personnel.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentJpaRepository extends JpaRepository<DepartmentEntity, Long> {
    List<DepartmentEntity> findAllByOrderByNameAsc();
}
