package com.example.quanlymuahang.pricing;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import com.example.quanlymuahang.domain.history.HistoricalPurchase;
import com.example.quanlymuahang.domain.material.MaterialCategory;
import com.example.quanlymuahang.domain.supplier.Supplier;
import com.example.quanlymuahang.repository.HistoricalPurchaseRepository;
import com.example.quanlymuahang.repository.SupplierRepository;
import com.example.quanlymuahang.service.TextNormalizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
class SupplierHistoryRepositoryTest {
    @Autowired HistoricalPurchaseRepository history;
    @Autowired SupplierRepository suppliers;

    @Test
    void findsHistoricalItemsByNormalizedSupplierNameAndCodeWithCurrencyFilter() {
        Supplier supplier = supplier("NCC-CT", "Công ty Cần Thơ");
        history.saveAndFlush(purchase(supplier, null, CurrencyCode.VND, "ROW-LINKED"));
        history.saveAndFlush(purchase(null, "NCC-OTHER", CurrencyCode.USD, "ROW-USD"));

        var result = history.searchBySupplier("cong ty can tho", "cong ty can tho", CurrencyCode.VND,
                PageRequest.of(0, 25));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(HistoricalPurchase::getSourceReference).containsExactly("ROW-LINKED");
    }

    @Test
    void findsSnapshotOnlySupplierRowsAndReturnsMultiplePurchaseLines() {
        history.saveAndFlush(purchase(null, "Công ty Cần Thơ", CurrencyCode.VND, "ROW-SNAPSHOT-1"));
        history.saveAndFlush(purchase(null, "Công ty Cần Thơ", CurrencyCode.VND, "ROW-SNAPSHOT-2"));

        var result = history.searchBySupplier("cong ty can tho", "Công ty Cần Thơ", null, PageRequest.of(0, 1));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    private Supplier supplier(String code, String name) {
        Supplier supplier = new Supplier(name, TextNormalizer.normalize(name));
        supplier.setCode(code);
        return suppliers.saveAndFlush(supplier);
    }

    private HistoricalPurchase purchase(Supplier supplier, String supplierSnapshot, CurrencyCode currency, String reference) {
        HistoricalPurchase purchase = new HistoricalPurchase();
        purchase.setSupplier(supplier);
        purchase.setSupplierSnapshot(supplierSnapshot != null ? supplierSnapshot : supplier.getName());
        purchase.setSupplierCodeSnapshot(supplier == null ? null : supplier.getCode());
        purchase.setPurchaseDate(LocalDate.of(2026, 10, 1));
        purchase.setMaterialNameSnapshot("Thép ống");
        purchase.setMaterialNameNormalizedSnapshot(TextNormalizer.normalize("Thép ống"));
        purchase.setUnit("m");
        purchase.setQuantity(new BigDecimal("2"));
        purchase.setUnitPrice(new BigDecimal("100"));
        purchase.setCurrency(currency);
        purchase.setCurrencyBasis("SOURCE");
        purchase.setSource("TEST");
        purchase.setSourceReference(reference);
        purchase.setCategory(MaterialCategory.MATERIAL);
        return purchase;
    }
}
