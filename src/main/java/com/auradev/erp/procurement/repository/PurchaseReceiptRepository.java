package com.auradev.erp.procurement.repository;
import com.auradev.erp.procurement.entity.PurchaseReceipt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface PurchaseReceiptRepository extends JpaRepository<PurchaseReceipt, UUID> {
    @EntityGraph(attributePaths = {"items", "items.product", "items.purchaseItem"})
    List<PurchaseReceipt> findByTenantIdAndPurchaseIdOrderByCreatedAtDesc(UUID tenantId, UUID purchaseId);
}
