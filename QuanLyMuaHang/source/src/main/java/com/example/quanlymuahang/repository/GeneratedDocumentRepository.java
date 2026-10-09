package com.example.quanlymuahang.repository;

import com.example.quanlymuahang.domain.purchaseorder.GeneratedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, Long> {
    Optional<GeneratedDocument> findByPurchaseOrderIdAndRevisionAndDocumentType(Long purchaseOrderId, int revision, String documentType);
}
