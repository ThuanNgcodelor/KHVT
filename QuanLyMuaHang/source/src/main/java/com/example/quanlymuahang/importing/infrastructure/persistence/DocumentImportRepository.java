package com.example.quanlymuahang.importing.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface DocumentImportRepository extends JpaRepository<DocumentImportEntity, Long> {
    Optional<DocumentImportEntity> findByChecksumAndKindAndSheetKey(String checksum, String kind, String sheetKey);
    List<DocumentImportEntity> findAllByOrderByIdDesc(Pageable pageable);
    List<DocumentImportEntity> findByKindAndStatusOrderByIdDesc(String kind, String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DocumentImportEntity d where d.id = :id")
    Optional<DocumentImportEntity> findLockedById(@Param("id") long id);
}
