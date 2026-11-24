package com.auradev.erp.procurement.repository;

import com.auradev.erp.procurement.entity.Purchase;
import com.auradev.erp.procurement.entity.PurchaseSequence;
import com.auradev.erp.procurement.entity.PurchaseSequenceId;
import com.auradev.erp.procurement.entity.PurchaseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.UUID;
import java.math.BigDecimal;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Purchase> findByIdAndTenantId(UUID id, UUID tenantId);

    @EntityGraph(attributePaths = {"payments"})
    java.util.List<Purchase> findByTenantIdAndSupplierIdAndStatusOrderByBillDateAsc(UUID tenantId, UUID supplierId, PurchaseStatus status);

    @Query("""
            SELECT new com.auradev.erp.procurement.dto.PurchaseSummaryResponse(
                p.id,
                p.purchaseNo,
                (SELECT s.name FROM Supplier s WHERE s.id = p.supplierId),
                p.billDate,
                p.dueDate,
                SIZE(p.items),
                p.grandTotal,
                p.status,
                p.createdAt)
            FROM Purchase p
            WHERE p.tenantId = :tenantId
              AND p.status IN :statuses
              AND p.supplierId = COALESCE(:supplierId, p.supplierId)
              AND (:q IS NULL OR :q = '' OR lower(p.purchaseNo) LIKE lower(concat('%', :q, '%')))
            ORDER BY p.billDate DESC, p.createdAt DESC
            """)
    Page<com.auradev.erp.procurement.dto.PurchaseSummaryResponse> searchSummaries(
            @Param("tenantId") UUID tenantId,
            @Param("q") String q,
            @Param("statuses") Collection<PurchaseStatus> statuses,
            @Param("supplierId") UUID supplierId,
            Pageable pageable);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, PurchaseStatus status);

    @Query("SELECT SUM(p.grandTotal) FROM Purchase p WHERE p.tenantId = :tenantId")
    BigDecimal sumGrandTotalByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT SUM(p.grandTotal) FROM Purchase p WHERE p.tenantId = :tenantId AND p.status = :status")
    BigDecimal sumGrandTotalByTenantIdAndStatus(
            @Param("tenantId") UUID tenantId,
            @Param("status") PurchaseStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM purchases
            WHERE id = :id AND tenant_id = :tenantId AND status = 'DRAFT'
            """, nativeQuery = true)
    int deleteDraftByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
