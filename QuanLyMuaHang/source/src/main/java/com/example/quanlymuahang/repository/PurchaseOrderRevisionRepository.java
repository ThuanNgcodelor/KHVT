package com.example.quanlymuahang.repository;

import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrderRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRevisionRepository extends JpaRepository<PurchaseOrderRevision, Long> {
    Optional<PurchaseOrderRevision> findByPurchaseOrderIdAndRevision(Long purchaseOrderId, int revision);
    List<PurchaseOrderRevision> findAllByPurchaseOrderIdOrderByRevisionDesc(Long purchaseOrderId);
}
