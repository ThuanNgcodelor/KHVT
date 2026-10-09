package com.example.quanlymuahang.repository;

import com.example.quanlymuahang.domain.supplier.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Optional<Supplier> findByCode(String code);
    Optional<Supplier> findByNormalizedName(String normalizedName);
    List<Supplier> findTop50ByActiveTrueAndNormalizedNameContainingOrderByNameAsc(String query);
    boolean existsByCodeIgnoreCase(String code);
    long countByActiveTrue();

    @Query("select s from Supplier s where (:q = '' or s.normalizedName like concat('%', :q, '%') " +
            "or lower(s.code) like concat('%', :q, '%')) and (:active is null or s.active = :active) " +
            "order by s.name asc, s.id asc")
    Page<Supplier> search(@Param("q") String query, @Param("active") Boolean active, Pageable pageable);
}
