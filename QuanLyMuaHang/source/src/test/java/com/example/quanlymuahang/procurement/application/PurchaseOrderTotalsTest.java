package com.example.quanlymuahang.procurement.application;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrder;
import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderTotalsTest {
    @Test void quantityTextIsPreservedButNotAddedToTotals() {
        PurchaseOrder order = new PurchaseOrder("PO-260101-01", LocalDate.of(2026, 1, 1), "NCC A");
        order.setCurrency(CurrencyCode.VND);
        order.setVatPercent(new BigDecimal("5"));
        order.addItem(new PurchaseOrderItem("Thép", "kg", new BigDecimal("1.5"), new BigDecimal("690000")));
        PurchaseOrderItem textQuantity = new PurchaseOrderItem("Sắt", "kg", null, new BigDecimal("100"));
        textQuantity.update(null, null, null, "Qua cân thực tế");
        order.addItem(textQuantity);

        PurchaseOrderService.OrderView view = PurchaseOrderService.OrderView.from(order);

        assertThat(view.items().get(1).quantityText()).isEqualTo("Qua cân thực tế");
        assertThat(view.items().get(1).lineTotal()).isNull();
        assertThat(view.quantityTextLineCount()).isEqualTo(1);
        assertThat(view.subtotal()).isEqualByComparingTo("1035000");
        assertThat(view.taxAmount()).isEqualByComparingTo("51750");
        assertThat(view.grandTotal()).isEqualByComparingTo("1086750");
    }

    @Test void usdAmountsRoundHalfUpToTwoPlaces() {
        PurchaseOrder order = new PurchaseOrder("PO-260101-02", LocalDate.of(2026, 1, 1), "NCC A");
        order.setCurrency(CurrencyCode.USD);
        order.setVatPercent(BigDecimal.ZERO);
        order.addItem(new PurchaseOrderItem("Part", "pc", BigDecimal.ONE, new BigDecimal("1.005")));

        assertThat(PurchaseOrderService.OrderView.from(order).grandTotal()).isEqualByComparingTo("1.01");
    }

    @Test void acceptsTenPercentVatRegardlessOfDecimalScale() {
        assertThat(PurchaseOrderService.isSupportedVat(new BigDecimal("10.00"))).isTrue();
        assertThat(PurchaseOrderService.isSupportedVat(new BigDecimal("7.00"))).isFalse();
        assertThat(PurchaseOrderService.isSupportedVat(null)).isFalse();
    }
}
