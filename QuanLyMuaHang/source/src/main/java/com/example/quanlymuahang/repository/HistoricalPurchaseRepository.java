package com.example.quanlymuahang.repository;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import com.example.quanlymuahang.domain.history.HistoricalPurchase;
import com.example.quanlymuahang.domain.material.MaterialCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HistoricalPurchaseRepository extends JpaRepository<HistoricalPurchase, Long> {
    boolean existsBySourceReference(String sourceReference);
    default Page<HistoricalPurchase> search(String normalizedQuery, CurrencyCode currency, Pageable pageable) {
        return searchFiltered(normalizedQuery, currency, null, pageable);
    }

    @Query("select h from HistoricalPurchase h where (:q is null or :q = '' or h.materialNameNormalizedSnapshot like concat('%', :q, '%') " +
            "or lower(h.materialNameSnapshot) like lower(concat('%', :q, '%')) or lower(h.materialCodeSnapshot) like concat('%', :q, '%')) " +
            "and (:currency is null or h.currency = :currency) and (:category is null or h.category = :category) " +
            "order by case when h.purchaseDate is null then 1 else 0 end, h.purchaseDate desc, h.id desc")
    Page<HistoricalPurchase> searchFiltered(@Param("q") String normalizedQuery, @Param("currency") CurrencyCode currency,
                                            @Param("category") MaterialCategory category, Pageable pageable);

    @Query("select h from HistoricalPurchase h where lower(h.materialCodeSnapshot) = lower(:code) " +
            "and h.currency = :currency and h.purchaseDate is not null order by h.purchaseDate desc, h.id desc")
    List<HistoricalPurchase> latestByCode(@Param("code") String code, @Param("currency") CurrencyCode currency, Pageable pageable);

    @Query("select h from HistoricalPurchase h where h.materialNameNormalizedSnapshot = :name " +
            "and h.currency = :currency and h.purchaseDate is not null order by h.purchaseDate desc, h.id desc")
    List<HistoricalPurchase> latestByName(@Param("name") String normalizedName, @Param("currency") CurrencyCode currency, Pageable pageable);
}
