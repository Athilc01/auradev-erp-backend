package com.auradev.erp.procurement.repository;

import com.auradev.erp.procurement.entity.PurchaseReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.UUID;

public interface PurchaseReturnItemRepository extends JpaRepository<PurchaseReturnItem, UUID> {
    @Query("select coalesce(sum(i.quantity), 0) from PurchaseReturnItem i where i.purchaseItem.id = :purchaseItemId")
    BigDecimal returnedQuantity(@Param("purchaseItemId") UUID purchaseItemId);
}
