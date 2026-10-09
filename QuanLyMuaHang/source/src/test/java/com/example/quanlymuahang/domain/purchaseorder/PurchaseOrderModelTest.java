package com.example.quanlymuahang.domain.purchaseorder;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderModelTest {

    @Test
    void addsItemsWithSequentialLineNumbers() {
        PurchaseOrder order = new PurchaseOrder("PO-260000-01", LocalDate.of(2026, 1, 1), "NCC test");
        order.addItem(new PurchaseOrderItem("Vật tư A", "kg", BigDecimal.ONE, BigDecimal.TEN));
        order.addItem(new PurchaseOrderItem("Vật tư B", "cái", BigDecimal.TWO, BigDecimal.ONE));

        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getItems().get(0).getLineNo()).isEqualTo(1);
        assertThat(order.getItems().get(1).getLineNo()).isEqualTo(2);
        assertThat(order.getItems().get(0).getPurchaseOrder()).isSameAs(order);
    }
}
