package com.example.quanlymuahang.domain.purchaseorder;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import com.example.quanlymuahang.domain.supplier.Supplier;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "po_number", nullable = false, unique = true, length = 40)
    private String poNumber;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "supplier_name_snapshot", nullable = false, length = 500)
    private String supplierNameSnapshot;

    @Column(name = "supplier_address_snapshot", length = 1000)
    private String supplierAddressSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode currency = CurrencyCode.VND;

    @Column(name = "vat_percent", precision = 5, scale = 2)
    private BigDecimal vatPercent = BigDecimal.ZERO;

    @Column(length = 1000)
    private String note;

    @Column(name = "prepared_by", length = 255)
    private String preparedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column(nullable = false)
    private int revision = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "source_po_number", length = 40)
    private String sourcePoNumber;

    @Column(name = "source_import_batch_id")
    private Long sourceImportBatchId;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<PurchaseOrderItem> items = new ArrayList<>();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(String poNumber, LocalDate orderDate, String supplierNameSnapshot) {
        this.poNumber = poNumber;
        this.orderDate = orderDate;
        this.supplierNameSnapshot = supplierNameSnapshot;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void addItem(PurchaseOrderItem item) {
        item.setPurchaseOrder(this);
        item.setLineNo(items.size() + 1);
        items.add(item);
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public void replaceItems(List<PurchaseOrderItem> replacement) {
        items.clear();
        replacement.forEach(this::addItem);
    }

    public void cancel(String reason) {
        this.status = PurchaseOrderStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.cancelReason = reason;
    }

    public void setCreatedBy(Long value) { this.createdBy = value; }
    public void setUpdatedBy(Long value) { this.updatedBy = value; }
    public void setSourcePoNumber(String value) { this.sourcePoNumber = value; }
    public void setSourceImportBatchId(Long value) { this.sourceImportBatchId = value; }

    public Long getId() { return id; }
    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }
    public LocalDate getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public String getSupplierNameSnapshot() { return supplierNameSnapshot; }
    public void setSupplierNameSnapshot(String value) { this.supplierNameSnapshot = value; }
    public String getSupplierAddressSnapshot() { return supplierAddressSnapshot; }
    public void setSupplierAddressSnapshot(String value) { this.supplierAddressSnapshot = value; }
    public CurrencyCode getCurrency() { return currency; }
    public void setCurrency(CurrencyCode currency) { this.currency = currency; }
    public BigDecimal getVatPercent() { return vatPercent; }
    public void setVatPercent(BigDecimal vatPercent) { this.vatPercent = vatPercent; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getPreparedBy() { return preparedBy; }
    public void setPreparedBy(String preparedBy) { this.preparedBy = preparedBy; }
    public PurchaseOrderStatus getStatus() { return status; }
    public void setStatus(PurchaseOrderStatus status) { this.status = status; }
    public int getRevision() { return revision; }
    public void setRevision(int revision) { this.revision = revision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<PurchaseOrderItem> getItems() { return items; }
    public Long getCreatedBy() { return createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public String getSourcePoNumber() { return sourcePoNumber; }
    public Long getSourceImportBatchId() { return sourceImportBatchId; }
    public long getVersion() { return version; }
}
