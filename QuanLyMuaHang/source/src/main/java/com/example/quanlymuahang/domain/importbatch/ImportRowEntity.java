package com.example.quanlymuahang.domain.importbatch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "import_rows", uniqueConstraints = @UniqueConstraint(name = "uk_import_rows_batch_sheet_row", columnNames = {"batch_id", "sheet_name", "row_number"}))
public class ImportRowEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "batch_id", nullable = false) private Long batchId;
    @Column(name = "sheet_name", nullable = false, length = 80) private String sheetName;
    @Column(name = "row_number", nullable = false) private int rowNumber;
    @Column(name = "raw_json", nullable = false, columnDefinition = "json") private String rawJson;
    @Column(name = "mapped_json", columnDefinition = "json") private String mappedJson;
    @Column(name = "issues_json", columnDefinition = "json") private String issuesJson;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "committed_entity_type", length = 60) private String committedEntityType;
    @Column(name = "committed_entity_id") private Long committedEntityId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected ImportRowEntity() {}
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
