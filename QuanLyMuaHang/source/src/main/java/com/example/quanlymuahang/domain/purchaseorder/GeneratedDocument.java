package com.example.quanlymuahang.domain.purchaseorder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "generated_documents")
public class GeneratedDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "purchase_order_id", nullable = false) private Long purchaseOrderId;
    @Column(nullable = false) private int revision;
    @Column(name = "document_type", nullable = false, length = 30) private String documentType;
    @Column(name = "file_name", nullable = false, length = 255) private String fileName;
    @Column(name = "storage_key", nullable = false, length = 1000) private String storageKey;
    @Column(name = "content_type", nullable = false, length = 120) private String contentType;
    @Column(nullable = false, length = 64) private String sha256;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "generated_by") private Long generatedBy;
    @Column(name = "generated_at", nullable = false) private Instant generatedAt;
    protected GeneratedDocument() {}
    public GeneratedDocument(Long poId, int revision, String type, String fileName, String storageKey, String contentType, String sha256, Long actorId) {
        this.purchaseOrderId = poId; this.revision = revision; this.documentType = type; this.fileName = fileName;
        this.storageKey = storageKey; this.contentType = contentType; this.sha256 = sha256; this.status = "READY"; this.generatedBy = actorId;
    }
    @PrePersist void onCreate() { generatedAt = Instant.now(); }
    public String getFileName() { return fileName; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
}
