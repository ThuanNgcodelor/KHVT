package com.example.quanlymuahang.domain.history;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import com.example.quanlymuahang.domain.material.Material;
import com.example.quanlymuahang.domain.material.MaterialCategory;
import com.example.quanlymuahang.domain.supplier.Supplier;
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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "historical_purchases")
public class HistoricalPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "supplier_snapshot", length = 500)
    private String supplierSnapshot;

    @Column(name = "supplier_code_snapshot", length = 50)
    private String supplierCodeSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    private Material material;

    @Column(name = "material_code_snapshot", length = 80)
    private String materialCodeSnapshot;

    @Column(name = "material_name_snapshot", nullable = false, length = 500)
    private String materialNameSnapshot;

    @Column(name = "material_name_normalized_snapshot", length = 500)
    private String materialNameNormalizedSnapshot;

    @Column(length = 100)
    private String unit;

    @Column(precision = 20, scale = 6)
    private BigDecimal quantity;

    @Column(name = "quantity_text", length = 255)
    private String quantityText;

    @Column(name = "unit_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode currency = CurrencyCode.VND;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "currency_basis", nullable = false, length = 30)
    private String currencyBasis = "SOURCE";

    @Column(name = "source_row_number")
    private Integer sourceRowNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaterialCategory category = MaterialCategory.MATERIAL;

    public Long getId() { return id; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public String getSupplierSnapshot() { return supplierSnapshot; }
    public void setSupplierSnapshot(String supplierSnapshot) { this.supplierSnapshot = supplierSnapshot; }
    public String getSupplierCodeSnapshot() { return supplierCodeSnapshot; }
    public void setSupplierCodeSnapshot(String value) { this.supplierCodeSnapshot = value; }
    public Material getMaterial() { return material; }
    public void setMaterial(Material material) { this.material = material; }
    public String getMaterialCodeSnapshot() { return materialCodeSnapshot; }
    public void setMaterialCodeSnapshot(String value) { this.materialCodeSnapshot = value; }
    public String getMaterialNameSnapshot() { return materialNameSnapshot; }
    public void setMaterialNameSnapshot(String value) { this.materialNameSnapshot = value; }
    public String getMaterialNameNormalizedSnapshot() { return materialNameNormalizedSnapshot; }
    public void setMaterialNameNormalizedSnapshot(String value) { this.materialNameNormalizedSnapshot = value; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getQuantityText() { return quantityText; }
    public void setQuantityText(String quantityText) { this.quantityText = quantityText; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public CurrencyCode getCurrency() { return currency; }
    public void setCurrency(CurrencyCode currency) { this.currency = currency; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getCurrencyBasis() { return currencyBasis; }
    public void setCurrencyBasis(String currencyBasis) { this.currencyBasis = currencyBasis; }
    public Integer getSourceRowNumber() { return sourceRowNumber; }
    public void setSourceRowNumber(Integer value) { this.sourceRowNumber = value; }
    public MaterialCategory getCategory() { return category; }
    public void setCategory(MaterialCategory category) { this.category = category; }
}
