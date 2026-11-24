package com.auradev.erp.inventory.repository;

import com.auradev.erp.inventory.entity.StockMovement;
import com.auradev.erp.inventory.entity.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.time.Instant;
import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Page<StockMovement> findByTenantIdAndProductIdOrderByCreatedAtDesc(
            UUID tenantId, UUID productId, Pageable pageable);

    Page<StockMovement> findByTenantIdAndMovementTypeOrderByCreatedAtDesc(
            UUID tenantId, MovementType movementType, Pageable pageable);

    Page<StockMovement> findByTenantIdAndMovementTypeInOrderByCreatedAtDesc(
            UUID tenantId, java.util.Collection<MovementType> movementTypes, Pageable pageable);

    @Query("SELECT COUNT(m) > 0 FROM StockMovement m WHERE m.product.id = :productId")
    boolean existsByProductId(@Param("productId") UUID productId);

    @Query("""
            SELECT m.product.id AS productId, MAX(m.createdAt) AS lastSale
            FROM StockMovement m
            WHERE m.tenantId = :tenantId AND m.movementType = :type
            GROUP BY m.product.id
            """)
    List<LastSaleProjection> findLastMovementByProduct(
            @Param("tenantId") UUID tenantId,
            @Param("type") MovementType type);

    interface LastSaleProjection {
        UUID getProductId();
        Instant getLastSale();
    }
}
