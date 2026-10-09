package com.example.quanlymuahang.domain.purchaseorder;

import com.example.quanlymuahang.domain.material.Material;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_items")
public class PurchaseOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    private Material material;

    @Column(name = "material_code_snapshot", length = 80)
    private String materialCodeSnapshot;

    @Column(name = "material_name", nullable = false, length = 500)
    private String materialName;

    @Column(length = 1000)
    private String specification;

    @Column(length = 100)
    private String unit;

    @Column(precision = 20, scale = 6)
    private BigDecimal quantity;

    @Column(name = "quantity_text", length = 255)
    private String quantityText;

    @Column(name = "unit_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal unitPrice;

    protected PurchaseOrderItem() {
    }

    public PurchaseOrderItem(String materialName, String unit, BigDecimal quantity, BigDecimal unitPrice) {
        this.materialName = materialName;
        this.unit = unit;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public void update(String code, String specification, BigDecimal quantity, String quantityText) {
        this.materialCodeSnapshot = code;
        this.specification = specification;
        this.quantity = quantity;
        this.quantityText = quantityText;
    }

    public void setMaterial(Material material) { this.material = material; }

    public Long getId() { return id; }
    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public void setPurchaseOrder(PurchaseOrder purchaseOrder) { this.purchaseOrder = purchaseOrder; }
    public int getLineNo() { return lineNo; }
    public void setLineNo(int lineNo) { this.lineNo = lineNo; }
    public Material getMaterial() { return material; }
    public void setMaterial(Material material) { this.material = material; }
    public String getMaterialCodeSnapshot() { return materialCodeSnapshot; }
    public void setMaterialCodeSnapshot(String value) { this.materialCodeSnapshot = value; }
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getQuantityText() { return quantityText; }
    public void setQuantityText(String quantityText) { this.quantityText = quantityText; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}
