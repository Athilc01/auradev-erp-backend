package com.auradev.erp.procurement.repository;
import com.auradev.erp.procurement.entity.PurchaseReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.UUID;
public interface PurchaseReceiptItemRepository extends JpaRepository<PurchaseReceiptItem, UUID> {
    @Query("select coalesce(sum(i.quantity), 0) from PurchaseReceiptItem i where i.purchaseItem.id = :purchaseItemId")
    BigDecimal receivedQuantity(@Param("purchaseItemId") UUID purchaseItemId);
}
