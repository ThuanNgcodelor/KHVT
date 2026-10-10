package com.example.quanlymuahang.pricing.application;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import com.example.quanlymuahang.domain.history.HistoricalPurchase;
import com.example.quanlymuahang.domain.material.MaterialCategory;
import com.example.quanlymuahang.repository.HistoricalPurchaseRepository;
import com.example.quanlymuahang.service.TextNormalizer;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
        return search(query, currency, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PriceView> search(String query, CurrencyCode currency, MaterialCategory category, Pageable pageable) {
        return history.searchFiltered(TextNormalizer.normalize(query), currency, category, pageable).map(PriceView::from);
    }

    @Transactional(readOnly = true)
    public PriceView latest(String materialCode, String materialName, CurrencyCode currency) {
        if (currency == null) throw ApiException.badRequest("CURRENCY_REQUIRED", "Chọn loại tiền khi tra giá gần nhất");
        boolean hasCode = materialCode != null && !materialCode.isBlank();
        String normalizedName = TextNormalizer.normalize(materialName);
        if (!hasCode && normalizedName.isBlank())
            throw ApiException.badRequest("MATERIAL_REQUIRED", "Nhập mã hoặc tên chính xác của vật tư");
        if (hasCode) {
            var byCode = history.latestByCode(materialCode.trim(), currency, PageRequest.of(0, 1));
            if (!byCode.isEmpty()) return PriceView.from(byCode.getFirst());
            throw ApiException.notFound("Chưa có giá có ngày cho mã vật tư và loại tiền đã chọn");
        }
        if (!normalizedName.isBlank()) {
            var byName = history.latestByName(normalizedName, currency, PageRequest.of(0, 1));
            if (!byName.isEmpty()) return PriceView.from(byName.getFirst());
        }
        throw ApiException.notFound("Chưa có giá có ngày cho vật tư và loại tiền đã chọn");
    }

    public record PriceView(Long id, LocalDate purchaseDate, String materialName, String materialCode, String unit,
                            BigDecimal quantity, String quantityText, BigDecimal unitPrice, CurrencyCode currency,
                            String currencyBasis, String supplierName, String supplierCode, String source,
                            String sourceSheet, Integer sourceRowNumber) {
        static PriceView from(HistoricalPurchase h) {
            String supplier = h.getSupplier() != null ? h.getSupplier().getName() : h.getSupplierSnapshot();
            String code = h.getSupplier() != null ? h.getSupplier().getCode() : h.getSupplierCodeSnapshot();
            return new PriceView(h.getId(), h.getPurchaseDate(), h.getMaterialNameSnapshot(), h.getMaterialCodeSnapshot(), h.getUnit(),
                    h.getQuantity(), h.getQuantityText(), h.getUnitPrice(), h.getCurrency(), h.getCurrencyBasis(), supplier, code,
                    h.getSource(), h.getSourceSheet(), h.getSourceRowNumber());
        }
    }
}
