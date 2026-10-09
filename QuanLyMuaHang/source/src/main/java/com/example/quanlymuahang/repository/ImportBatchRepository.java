package com.example.quanlymuahang.repository;

import com.example.quanlymuahang.domain.importbatch.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    Optional<ImportBatch> findBySha256AndMode(String sha256, String mode);
}
