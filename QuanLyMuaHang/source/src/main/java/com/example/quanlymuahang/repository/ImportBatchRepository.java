package com.example.quanlymuahang.repository;

import com.example.quanlymuahang.domain.importbatch.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    Optional<ImportBatch> findBySha256AndMode(String sha256, String mode);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select batch from ImportBatch batch where batch.id = :id")
    Optional<ImportBatch> findLockedById(long id);
}
