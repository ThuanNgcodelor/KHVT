package com.example.quanlymuahang.repository;

import com.example.quanlymuahang.domain.material.Material;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long> {
    Optional<Material> findByCode(String code);
    Optional<Material> findByNormalizedName(String normalizedName);
    List<Material> findTop20ByActiveTrueAndNormalizedNameContainingIgnoreCaseOrderByNameAsc(String query);
}
