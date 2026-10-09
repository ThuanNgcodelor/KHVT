package com.example.quanlymuahang.personnel.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PositionJpaRepository extends JpaRepository<PositionEntity, Long> {
    List<PositionEntity> findAllByOrderByNameAsc();
}
