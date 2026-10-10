package com.example.quanlymuahang.repository;

import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    boolean existsByPoNumber(String poNumber);
    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseOrder p where p.id = :id")
    Optional<PurchaseOrder> findLockedById(@Param("id") long id);

    @Query("select p from PurchaseOrder p where (:q is null or :q = '' or lower(p.poNumber) like lower(concat('%', :q, '%')) or lower(p.supplierNameSnapshot) like lower(concat('%', :q, '%'))) and (:status is null or p.status = :status) order by p.orderDate desc, p.id desc")
    Page<PurchaseOrder> search(@Param("q") String query, @Param("status") com.example.quanlymuahang.domain.purchaseorder.PurchaseOrderStatus status, Pageable pageable);

    long countByOrderDateBetween(java.time.LocalDate from, java.time.LocalDate to);
}
