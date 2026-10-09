package com.example.quanlymuahang.pricing.application;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import com.example.quanlymuahang.domain.history.HistoricalPurchase;
import com.example.quanlymuahang.repository.HistoricalPurchaseRepository;
import com.example.quanlymuahang.service.TextNormalizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class PricingService {
    private final HistoricalPurchaseRepository history;
    public PricingService(HistoricalPurchaseRepository history) { this.history = history; }

    @Transactional(readOnly = true)
    public Page<PriceView> search(String query, CurrencyCode currency, Pageable pageable) {
        return history.search(TextNormalizer.normalize(query), currency, pageable).map(PriceView::from);
    }

    public record PriceView(Long id, LocalDate purchaseDate, String materialName, String materialCode, String unit,
                            BigDecimal quantity, String quantityText, BigDecimal unitPrice, CurrencyCode currency,
                            String supplierName, String supplierCode, String source, Integer sourceRowNumber) {
        static PriceView from(HistoricalPurchase h) {
            String supplier = h.getSupplier() != null ? h.getSupplier().getName() : h.getSupplierSnapshot();
            String code = h.getSupplier() != null ? h.getSupplier().getCode() : h.getSupplierCodeSnapshot();
            return new PriceView(h.getId(), h.getPurchaseDate(), h.getMaterialNameSnapshot(), h.getMaterialCodeSnapshot(), h.getUnit(),
                    h.getQuantity(), h.getQuantityText(), h.getUnitPrice(), h.getCurrency(), supplier, code, h.getSource(), h.getSourceRowNumber());
        }
    }
}
