package com.example.quanlymuahang.importing.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "source_document_imports", uniqueConstraints = @UniqueConstraint(name = "uk_source_document", columnNames = {"checksum", "kind", "sheet_key"}))
public class DocumentImportEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 20) private String kind;
    @Column(nullable = false, length = 255) private String title;
    @Column(name = "file_name", nullable = false, length = 255) private String fileName;
    @Column(nullable = false, length = 64) private String checksum;
    @Column(name = "sheet_key", nullable = false, length = 80) private String sheetKey;
    @Column(name = "storage_key", nullable = false, length = 200) private String storageKey;
    @Column(name = "parsed_json", nullable = false, columnDefinition = "longtext") private String parsedJson;
    @Column(name = "confirmed_json", columnDefinition = "longtext") private String confirmedJson;
    @Column(name = "inquiries_json", nullable = false, columnDefinition = "longtext") private String inquiriesJson = "[]";
    @Column(nullable = false, length = 20) private String status = "PREVIEW";
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;
    protected DocumentImportEntity() {}
    public DocumentImportEntity(String kind, String fileName, String checksum, String sheetKey, String storageKey, String parsedJson, Long actor) {
        this.kind = kind; this.fileName = fileName; this.title = fileName; this.checksum = checksum;
        this.sheetKey = sheetKey; this.storageKey = storageKey; this.parsedJson = parsedJson; this.createdBy = actor;
        this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
    public void confirm(String title, String json) { this.title = title; this.confirmedJson = json; this.status = "CONFIRMED"; this.updatedAt = Instant.now(); }
    public void inquiries(String json) { this.inquiriesJson = json; this.updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public String getKind() { return kind; }
    public String getTitle() { return title; }
    public String getFileName() { return fileName; }
    public String getChecksum() { return checksum; }
    public String getSheetKey() { return sheetKey; }
    public String getStorageKey() { return storageKey; }
    public String getParsedJson() { return parsedJson; }
    public String getConfirmedJson() { return confirmedJson; }
    public String getInquiriesJson() { return inquiriesJson; }
    public String getStatus() { return status; }
}
