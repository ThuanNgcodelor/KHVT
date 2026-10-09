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
@Table(name = "purchase_order_revisions")
public class PurchaseOrderRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "purchase_order_id", nullable = false) private Long purchaseOrderId;
    @Column(nullable = false) private int revision;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "json") private String snapshotJson;
    @Column(name = "changed_by") private Long changedBy;
    @Column(name = "change_reason", length = 500) private String changeReason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected PurchaseOrderRevision() {}
    public PurchaseOrderRevision(Long purchaseOrderId, int revision, String snapshotJson, Long changedBy, String changeReason) {
        this.purchaseOrderId = purchaseOrderId; this.revision = revision; this.snapshotJson = snapshotJson; this.changedBy = changedBy; this.changeReason = changeReason;
    }
    @PrePersist void onCreate() { createdAt = Instant.now(); }
    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public int getRevision() { return revision; }
    public String getSnapshotJson() { return snapshotJson; }
    public Long getChangedBy() { return changedBy; }
    public String getChangeReason() { return changeReason; }
    public Instant getCreatedAt() { return createdAt; }
}
