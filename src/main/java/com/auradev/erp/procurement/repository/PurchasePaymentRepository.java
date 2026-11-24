package com.auradev.erp.procurement.repository;

import com.auradev.erp.procurement.entity.PurchasePayment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface PurchasePaymentRepository extends JpaRepository<PurchasePayment, UUID> {
    @EntityGraph(attributePaths = "purchase")
    List<PurchasePayment> findByTenantIdAndPurchase_SupplierIdOrderByCreatedAtDesc(UUID tenantId, UUID supplierId);
}
