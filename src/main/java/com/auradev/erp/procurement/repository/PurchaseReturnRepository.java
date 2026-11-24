package com.auradev.erp.procurement.repository;

import com.auradev.erp.procurement.entity.PurchaseReturn;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, UUID> {
    @EntityGraph(attributePaths = {"purchase", "items", "items.purchaseItem", "items.product"})
    List<PurchaseReturn> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
