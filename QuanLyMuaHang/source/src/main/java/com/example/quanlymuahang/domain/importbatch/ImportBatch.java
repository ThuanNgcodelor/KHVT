package com.example.quanlymuahang.domain.importbatch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;

import java.time.Instant;

@Entity
@Table(name = "import_batches")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(nullable = false, length = 30)
    private String mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportBatchStatus status = ImportBatchStatus.PREVIEW;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "success_rows", nullable = false)
    private int successRows;

    @Column(name = "error_rows", nullable = false)
    private int errorRows;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    protected ImportBatch() {
    }

    public ImportBatch(String fileName, String sha256, String fileType, String mode) {
        this.fileName = fileName;
        this.sha256 = sha256;
        this.fileType = fileType;
        this.mode = mode;
    }

    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public String getFileName() { return fileName; }
    public String getSha256() { return sha256; }
    public String getFileType() { return fileType; }
    public String getMode() { return mode; }
    public ImportBatchStatus getStatus() { return status; }
    public void setStatus(ImportBatchStatus status) { this.status = status; }
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int value) { this.totalRows = value; }
    public int getSuccessRows() { return successRows; }
    public void setSuccessRows(int value) { this.successRows = value; }
    public int getErrorRows() { return errorRows; }
    public void setErrorRows(int value) { this.errorRows = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
