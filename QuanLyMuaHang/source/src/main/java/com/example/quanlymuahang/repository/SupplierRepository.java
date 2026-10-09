package com.example.quanlymuahang.repository;

import com.example.quanlymuahang.domain.supplier.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Optional<Supplier> findByCode(String code);
    Optional<Supplier> findByNormalizedName(String normalizedName);
    List<Supplier> findTop50ByActiveTrueAndNormalizedNameContainingOrderByNameAsc(String query);
    boolean existsByCodeIgnoreCase(String code);
}
