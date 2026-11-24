package com.auradev.erp.inventory.service;

import com.auradev.erp.auth.security.UserPrincipal;
import com.auradev.erp.catalog.entity.Product;
import com.auradev.erp.catalog.repository.ProductRepository;
import com.auradev.erp.common.error.BusinessException;
import com.auradev.erp.common.error.EntityNotFoundException;
import com.auradev.erp.common.pagination.PageResponse;
import com.auradev.erp.inventory.dto.SaleMovementLine;
import com.auradev.erp.inventory.dto.StockAdjustRequest;
import com.auradev.erp.inventory.dto.OpeningStockLineRequest;
import com.auradev.erp.inventory.dto.StockMovementResponse;
import com.auradev.erp.inventory.dto.BatchReceiptRequest;
import com.auradev.erp.inventory.dto.InventoryBatchResponse;
import com.auradev.erp.inventory.dto.InventoryHealthResponse;
import com.auradev.erp.inventory.entity.InventoryBatch;
import com.auradev.erp.inventory.entity.Inventory;
import com.auradev.erp.inventory.entity.MovementType;
import com.auradev.erp.inventory.entity.RefType;
import com.auradev.erp.inventory.entity.StockMovement;
import com.auradev.erp.inventory.repository.InventoryRepository;
import com.auradev.erp.inventory.repository.StockMovementRepository;
import com.auradev.erp.inventory.repository.InventoryBatchRepository;
import com.auradev.erp.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository movementRepository;
    private final InventoryBatchRepository batchRepository;

    public StockMovement recordMovement(
            UUID productId,
            MovementType movementType,
            BigDecimal quantity,
            RefType refType,
            UUID refId,
            String notes) {

        UUID tenantId = TenantContext.require();
        UUID userId = requireCurrentUserId();

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_QUANTITY", "Quantity must be positive");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product", productId));

        Inventory inv = inventoryRepository.findForUpdate(tenantId, productId)
                .orElseGet(() -> createInventoryRow(tenantId, productId));

        if (isStockOut(movementType)) {
            deductTrackedBatchesIfRequired(tenantId, product, quantity);
            int updated = inventoryRepository.decrement(tenantId, productId, quantity);
            if (updated == 0) {
                throw new BusinessException(
                        "INSUFFICIENT_STOCK",
                        "Insufficient stock for product " + productId);
            }
        } else {
            inventoryRepository.increment(tenantId, productId, quantity);
        }

        Inventory refreshed = inventoryRepository.findByTenantIdAndProductId(tenantId, productId)
                .orElseThrow(() -> new EntityNotFoundException("Inventory", productId));

        StockMovement movement = new StockMovement();
        movement.setTenantId(tenantId);
        movement.setProduct(refreshed.getProduct());
        movement.setMovementType(movementType);
        movement.setQuantity(quantity);
        movement.setQuantityAfter(refreshed.getQuantityOnHand());
        movement.setReferenceType(refType != null ? refType : RefType.manual);
        movement.setReferenceId(refId);
        movement.setNotes(notes);
        movement.setCreatedBy(userId);
        movement.setCreatedAt(Instant.now());

        StockMovement saved = movementRepository.save(movement);
        log.info("Movement recorded product={} type={} qty={} balance={}",
                productId, movementType, quantity, refreshed.getQuantityOnHand());
        return saved;
    }

    /**
     * Records all sale stock movements for a bill in one locked batch — avoids per-line round trips.
     */
    public void recordSaleMovements(
            List<SaleMovementLine> lines,
            RefType refType,
            UUID refId,
            String notes) {
        if (lines == null || lines.isEmpty()) {
            return;
        }

        UUID tenantId = TenantContext.require();
        UUID userId = requireCurrentUserId();
        Instant now = Instant.now();

        Map<UUID, BigDecimal> qtyByProduct = new LinkedHashMap<>();
        for (SaleMovementLine line : lines) {
            if (line.quantity() == null || line.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("INVALID_QUANTITY", "Quantity must be positive");
            }
            qtyByProduct.merge(line.productId(), line.quantity(), BigDecimal::add);
        }

        List<UUID> productIds = new ArrayList<>(qtyByProduct.keySet());
        Map<UUID, Inventory> locked = inventoryRepository.findForUpdateByProductIds(tenantId, productIds).stream()
                .collect(Collectors.toMap(inv -> inv.getProduct().getId(), inv -> inv));

        List<StockMovement> movements = new ArrayList<>(productIds.size());
        for (UUID productId : productIds) {
            BigDecimal qty = qtyByProduct.get(productId);
            Inventory inv = locked.get(productId);
            if (inv == null) {
                throw new BusinessException(
                        "INSUFFICIENT_STOCK",
                        "Insufficient stock for product " + productId);
            }

            BigDecimal onHand = inv.getQuantityOnHand();
            if (onHand.compareTo(qty) < 0) {
                throw new BusinessException(
                        "INSUFFICIENT_STOCK",
                        "Insufficient stock for product " + productId);
            }

            deductTrackedBatchesIfRequired(tenantId, inv.getProduct(), qty);

            int updated = inventoryRepository.decrement(tenantId, productId, qty);
            if (updated == 0) {
                throw new BusinessException(
                        "INSUFFICIENT_STOCK",
                        "Insufficient stock for product " + productId);
            }

            StockMovement movement = new StockMovement();
            movement.setTenantId(tenantId);
            movement.setProduct(inv.getProduct());
            movement.setMovementType(MovementType.sale);
            movement.setQuantity(qty);
            movement.setQuantityAfter(onHand.subtract(qty));
            movement.setReferenceType(refType != null ? refType : RefType.manual);
            movement.setReferenceId(refId);
            movement.setNotes(notes);
            movement.setCreatedBy(userId);
            movement.setCreatedAt(now);
            movements.add(movement);
        }

        movementRepository.saveAll(movements);
    }

    /**
     * Applies FEFO (first-expiry, first-out) to products that have batch tracking
     * enabled. The product-level inventory remains the source for the overall
     * quantity, while these locked batch rows determine which physical lots leave
     * the store.
     */
    private void deductTrackedBatchesIfRequired(UUID tenantId, Product product, BigDecimal quantity) {
        if (!product.isExpiryTracked()) {
            return;
        }

        List<InventoryBatch> batches = batchRepository.findSaleableForUpdate(
                tenantId, product.getId(), LocalDate.now());
        BigDecimal available = batches.stream()
                .map(InventoryBatch::getQuantityOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(quantity) < 0) {
            throw new BusinessException(
                    "INSUFFICIENT_SALEABLE_BATCH_STOCK",
                    "Only " + available.stripTrailingZeros().toPlainString() + " saleable batch stock is available for "
                            + product.getName() + ". Receive a valid batch before selling more.");
        }

        BigDecimal remaining = quantity;
        for (InventoryBatch batch : batches) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal taken = batch.getQuantityOnHand().min(remaining);
            batch.setQuantityOnHand(batch.getQuantityOnHand().subtract(taken));
            remaining = remaining.subtract(taken);
        }
        // Managed, pessimistically locked entities are flushed together with the
        // bill transaction. No separate save is needed and concurrent sales cannot
        // allocate the same lot twice.
    }

    public void ensureInventoryRow(UUID tenantId, UUID productId, BigDecimal initialStock,
                                   BigDecimal lowStockThreshold, BigDecimal reorderQuantity) {
        if (inventoryRepository.findByTenantIdAndProductId(tenantId, productId).isPresent()) {
            return;
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product", productId));

        Inventory inv = new Inventory();
        inv.setTenantId(tenantId);
        inv.setProduct(product);
        inv.setQuantityOnHand(initialStock != null ? initialStock : BigDecimal.ZERO);
        inv.setLowStockThreshold(lowStockThreshold);
        inv.setReorderQuantity(reorderQuantity);
        inv.setLastUpdated(Instant.now());
        inventoryRepository.save(inv);
    }

    public void updateThresholds(UUID tenantId, UUID productId,
                                 BigDecimal lowStockThreshold, BigDecimal reorderQuantity) {
        Inventory inv = inventoryRepository.findByTenantIdAndProductId(tenantId, productId)
                .orElseThrow(() -> new EntityNotFoundException("Inventory", productId));
        inv.setLowStockThreshold(lowStockThreshold);
        inv.setReorderQuantity(reorderQuantity);
        inv.setLastUpdated(Instant.now());
        inventoryRepository.save(inv);
    }

    @Transactional(readOnly = true)
    public Inventory getInventory(UUID tenantId, UUID productId) {
        return inventoryRepository.findByTenantIdAndProductId(tenantId, productId).orElse(null);
    }

    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> getMovements(UUID productId, Pageable pageable) {
        UUID tenantId = TenantContext.require();
        Page<StockMovement> page = movementRepository
                .findByTenantIdAndProductIdOrderByCreatedAtDesc(tenantId, productId, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> getOpeningStock(Pageable pageable) {
        UUID tenantId = TenantContext.require();
        Page<StockMovement> page = movementRepository.findByTenantIdAndMovementTypeOrderByCreatedAtDesc(
                tenantId, MovementType.opening_stock, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> getTransactions(List<MovementType> types, Pageable pageable) {
        if (types == null || types.isEmpty()) {
            throw new BusinessException("MOVEMENT_TYPE_REQUIRED", "Choose at least one transaction type");
        }
        UUID tenantId = TenantContext.require();
        Page<StockMovement> page = movementRepository.findByTenantIdAndMovementTypeInOrderByCreatedAtDesc(
                tenantId, types, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public boolean hasMovements(UUID productId) {
        return movementRepository.existsByProductId(productId);
    }

    public void bulkAdjust(List<StockAdjustRequest> adjustments) {
        for (StockAdjustRequest req : adjustments) {
            recordMovement(req.productId(), req.movementType(), req.quantity(),
                    RefType.manual, null, req.notes());
        }
    }

    /** Records an opening balance for several items as one inventory operation. */
    public void recordOpeningStock(List<OpeningStockLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("OPENING_STOCK_EMPTY", "Add at least one item to opening stock");
        }

        UUID tenantId = TenantContext.require();
        UUID userId = requireCurrentUserId();
        UUID openingStockId = UUID.randomUUID();
        for (OpeningStockLineRequest line : lines) {
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> new EntityNotFoundException("Product", line.productId()));
            String batchNumber = line.batchNumber() == null ? "" : line.batchNumber().trim();

            if (line.expiryDate() != null && batchNumber.isBlank()) {
                throw new BusinessException("BATCH_NUMBER_REQUIRED", "Enter a batch number when an expiry date is provided");
            }
            if (!batchNumber.isBlank() && !product.isExpiryTracked()) {
                throw new BusinessException("BATCH_NOT_ENABLED", "Enable batch and expiry tracking for " + product.getName());
            }
            if (line.expiryDate() != null && line.expiryDate().isBefore(LocalDate.now())) {
                throw new BusinessException("EXPIRED_BATCH", "An already expired batch cannot be added to opening stock");
            }

            recordMovement(line.productId(), MovementType.opening_stock, line.quantity(),
                    RefType.manual, openingStockId, line.notes());

            if (!batchNumber.isBlank()) {
                receiveIntoBatch(tenantId, userId, product, batchNumber, line.expiryDate(), line.quantity(), line.notes());
            }
        }
    }

    public InventoryBatchResponse recordBatchReceipt(UUID productId, BatchReceiptRequest req) {
        UUID tenantId = TenantContext.require();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product", productId));
        if (!product.isExpiryTracked()) {
            throw new BusinessException("BATCH_NOT_ENABLED", "Enable batch and expiry tracking for this item before receiving a batch");
        }
        if (req.expiryDate() != null && req.expiryDate().isBefore(LocalDate.now())) {
            throw new BusinessException("EXPIRED_BATCH", "An already expired batch cannot be received into saleable stock");
        }

        recordMovement(productId, MovementType.stock_receipt, req.quantity(), RefType.manual, null,
                "Batch " + req.batchNumber().trim() + (req.notes() == null || req.notes().isBlank() ? "" : " · " + req.notes().trim()));

        InventoryBatch batch = receiveIntoBatch(tenantId, requireCurrentUserId(), product,
                req.batchNumber().trim(), req.expiryDate(), req.quantity(), req.notes());
        return toBatchResponse(batch);
    }

    /** Records a batch already accounted for by a Purchase GRN stock movement. */
    public void receivePurchaseBatch(UUID productId, String batchNumber, LocalDate expiryDate, BigDecimal quantity, String notes) {
        UUID tenantId = TenantContext.require();
        Product product = productRepository.findById(productId).orElseThrow(() -> new EntityNotFoundException("Product", productId));
        if (!product.isExpiryTracked()) return;
        if (batchNumber == null || batchNumber.isBlank()) throw new BusinessException("BATCH_NUMBER_REQUIRED", "Enter a batch number for " + product.getName());
        if (expiryDate == null) throw new BusinessException("EXPIRY_DATE_REQUIRED", "Enter an expiry date for " + product.getName());
        if (expiryDate.isBefore(LocalDate.now())) throw new BusinessException("EXPIRED_BATCH", "An expired batch cannot be received");
        receiveIntoBatch(tenantId, requireCurrentUserId(), product, batchNumber.trim(), expiryDate, quantity, notes);
    }

    @Transactional(readOnly = true)
    public List<InventoryBatchResponse> getBatches(UUID productId) {
        UUID tenantId = TenantContext.require();
        productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product", productId));
        return batchRepository.findByTenantIdAndProductIdOrderByExpiryDateAsc(tenantId, productId).stream()
                .map(this::toBatchResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryHealthResponse health() {
        UUID tenantId = TenantContext.require();
        Instant slowCutoff = Instant.now().minus(java.time.Duration.ofDays(30));
        Map<UUID, Instant> lastSaleByProduct = movementRepository
                .findLastMovementByProduct(tenantId, MovementType.sale)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        StockMovementRepository.LastSaleProjection::getProductId,
                        StockMovementRepository.LastSaleProjection::getLastSale));
        List<InventoryHealthResponse.StockRisk> slow = inventoryRepository.findByTenantId(tenantId).stream()
                .filter(inv -> inv.getQuantityOnHand().signum() > 0)
                .map(inv -> { Instant lastSale = lastSaleByProduct.get(inv.getProduct().getId());
                    return new InventoryHealthResponse.StockRisk(inv.getProduct().getId(), inv.getProduct().getName(), inv.getProduct().getSku(), inv.getQuantityOnHand(), inv.getQuantityOnHand().multiply(inv.getProduct().getCostPrice()), lastSale, lastSale == null ? "Dead stock" : (lastSale.isBefore(slowCutoff) ? "Slow moving" : "Active")); })
                .filter(row -> !"Active".equals(row.classification())).toList();
        LocalDate today = LocalDate.now();
        List<InventoryHealthResponse.ExpiryRisk> expiry = batchRepository.findByTenantIdAndExpiryDateBetweenAndQuantityOnHandGreaterThan(tenantId, today, today.plusDays(30), BigDecimal.ZERO).stream()
                .map(batch -> new InventoryHealthResponse.ExpiryRisk(batch.getProduct().getId(), batch.getProduct().getName(), batch.getProduct().getSku(), batch.getBatchNumber(), batch.getExpiryDate(), batch.getQuantityOnHand(), batch.getQuantityOnHand().multiply(batch.getProduct().getCostPrice()), java.time.temporal.ChronoUnit.DAYS.between(today, batch.getExpiryDate()))).toList();
        return new InventoryHealthResponse(slow, expiry);
    }

    private Inventory createInventoryRow(UUID tenantId, UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product", productId));
        Inventory inv = new Inventory();
        inv.setTenantId(tenantId);
        inv.setProduct(product);
        inv.setQuantityOnHand(BigDecimal.ZERO);
        inv.setLastUpdated(Instant.now());
        return inventoryRepository.save(inv);
    }

    /** Adds a receipt to an existing lot when the supplier delivers the same batch again. */
    private InventoryBatch receiveIntoBatch(UUID tenantId, UUID userId, Product product, String batchNumber,
                                            LocalDate expiryDate, BigDecimal quantity, String notes) {
        InventoryBatch batch = batchRepository
                .findByTenantIdAndProductIdAndBatchNumber(tenantId, product.getId(), batchNumber)
                .orElseGet(() -> {
                    InventoryBatch created = new InventoryBatch();
                    created.setTenantId(tenantId);
                    created.setProduct(product);
                    created.setBatchNumber(batchNumber);
                    created.setExpiryDate(expiryDate);
                    created.setReceivedDate(LocalDate.now());
                    created.setQuantityReceived(BigDecimal.ZERO);
                    created.setQuantityOnHand(BigDecimal.ZERO);
                    created.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
                    created.setCreatedAt(Instant.now());
                    created.setCreatedBy(userId);
                    return created;
                });

        if (batch.getExpiryDate() != null && expiryDate != null && !batch.getExpiryDate().equals(expiryDate)) {
            throw new BusinessException("BATCH_EXPIRY_MISMATCH",
                    "Batch " + batchNumber + " already has expiry date " + batch.getExpiryDate());
        }
        if (batch.getExpiryDate() == null && expiryDate != null) {
            batch.setExpiryDate(expiryDate);
        }
        batch.setQuantityReceived(batch.getQuantityReceived().add(quantity));
        batch.setQuantityOnHand(batch.getQuantityOnHand().add(quantity));
        if (notes != null && !notes.isBlank()) {
            batch.setNotes(notes.trim());
        }
        return batchRepository.save(batch);
    }

    private boolean isStockOut(MovementType type) {
        return type == MovementType.sale
                || type == MovementType.adjustment_out
                || type == MovementType.count_out
                || type == MovementType.purchase_return
                || type == MovementType.waste;
    }

    private StockMovementResponse toResponse(StockMovement m) {
        BigDecimal signed = isStockOut(m.getMovementType())
                ? m.getQuantity().negate()
                : m.getQuantity();
        return new StockMovementResponse(
                m.getId(),
                m.getProduct().getName(),
                m.getProduct().getSku(),
                m.getMovementType(),
                m.getQuantity(),
                signed,
                m.getReferenceId(),
                m.getQuantityAfter(),
                m.getNotes(),
                m.getCreatedAt()
        );
    }

    private InventoryBatchResponse toBatchResponse(InventoryBatch batch) {
        return new InventoryBatchResponse(batch.getId(), batch.getBatchNumber(), batch.getExpiryDate(),
                batch.getReceivedDate(), batch.getQuantityReceived(), batch.getQuantityOnHand(), batch.getNotes());
    }

    private UUID requireCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal up) {
            return up.getId();
        }
        throw new BusinessException("UNAUTHENTICATED", "User context required for stock movements");
    }
}
