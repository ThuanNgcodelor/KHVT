package com.example.quanlymuahang.repository;

import com.example.quanlymuahang.domain.material.Material;
import com.example.quanlymuahang.domain.material.MaterialCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long> {
    Optional<Material> findByCode(String code);
    Optional<Material> findByNormalizedName(String normalizedName);
    List<Material> findTop20ByActiveTrueAndNormalizedNameContainingIgnoreCaseOrderByNameAsc(String query);
    List<Material> findTop50ByActiveTrueAndNormalizedNameContainingOrderByNameAsc(String query);
    boolean existsByCodeIgnoreCase(String code);
    long countByActiveTrue();

    @Query("select m from Material m where (:q = '' or m.normalizedName like concat('%', :q, '%') " +
            "or lower(m.code) like concat('%', :q, '%')) and (:active is null or m.active = :active) " +
            "and (:category is null or m.category = :category) order by m.name asc, m.id asc")
    Page<Material> search(@Param("q") String query, @Param("active") Boolean active,
                          @Param("category") MaterialCategory category, Pageable pageable);
}
