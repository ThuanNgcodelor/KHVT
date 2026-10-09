package com.example.quanlymuahang.repository;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import com.example.quanlymuahang.domain.history.HistoricalPurchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HistoricalPurchaseRepository extends JpaRepository<HistoricalPurchase, Long> {
    @Query("select h from HistoricalPurchase h where (:q is null or :q = '' or h.materialNameNormalizedSnapshot like concat('%', :q, '%') or lower(h.materialNameSnapshot) like lower(concat('%', :q, '%'))) and (:currency is null or h.currency = :currency) order by h.purchaseDate desc nulls last, h.id desc")
    Page<HistoricalPurchase> search(@Param("q") String normalizedQuery, @Param("currency") CurrencyCode currency, Pageable pageable);
}
