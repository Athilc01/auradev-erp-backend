package com.auradev.erp.inventory.repository;

import com.auradev.erp.inventory.entity.InventoryBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, UUID> {
    List<InventoryBatch> findByTenantIdAndProductIdOrderByExpiryDateAsc(UUID tenantId, UUID productId);
    List<InventoryBatch> findByTenantIdAndExpiryDateBetweenAndQuantityOnHandGreaterThan(UUID tenantId, LocalDate from, LocalDate to, java.math.BigDecimal quantity);

    Optional<InventoryBatch> findByTenantIdAndProductIdAndBatchNumber(
            UUID tenantId, UUID productId, String batchNumber);

    /**
     * Locks saleable batches in FEFO order: the earliest dated batch is used first,
     * while undated batches are used last. Expired and empty batches are excluded.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM InventoryBatch b
            WHERE b.tenantId = :tenantId
              AND b.product.id = :productId
              AND b.quantityOnHand > 0
              AND (b.expiryDate IS NULL OR b.expiryDate >= :today)
            ORDER BY CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END, b.expiryDate ASC, b.receivedDate ASC, b.id ASC
            """)
    List<InventoryBatch> findSaleableForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("productId") UUID productId,
            @Param("today") LocalDate today);
}
