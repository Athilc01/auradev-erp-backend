package com.auradev.erp.procurement.service;

import com.auradev.erp.audit.service.AuditService;
import com.auradev.erp.auth.security.UserPrincipal;
import com.auradev.erp.catalog.entity.Supplier;
import com.auradev.erp.catalog.repository.SupplierRepository;
import com.auradev.erp.common.error.BusinessException;
import com.auradev.erp.common.error.EntityNotFoundException;
import com.auradev.erp.common.util.MoneyUtils;
import com.auradev.erp.inventory.entity.MovementType;
import com.auradev.erp.inventory.entity.RefType;
import com.auradev.erp.inventory.service.InventoryService;
import com.auradev.erp.procurement.dto.*;
import com.auradev.erp.procurement.entity.*;
import com.auradev.erp.procurement.repository.PurchaseRepository;
import com.auradev.erp.procurement.repository.PurchaseReturnItemRepository;
import com.auradev.erp.procurement.repository.PurchaseReturnRepository;
import com.auradev.erp.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service @Transactional @RequiredArgsConstructor
public class PurchaseReturnService {
    private final PurchaseRepository purchaseRepository;
    private final PurchaseReturnRepository returnRepository;
    private final PurchaseReturnItemRepository returnItemRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    public PurchaseReturnResponse create(UUID purchaseId, CreatePurchaseReturnRequest req) {
        UUID tenantId = TenantContext.require();
        Purchase purchase = purchaseRepository.findByIdAndTenantId(purchaseId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase", purchaseId));
        if (purchase.getStatus() != PurchaseStatus.BILLED && purchase.getStatus() != PurchaseStatus.PAID) {
            throw new BusinessException("INVALID_STATUS", "Only received purchases can be returned to a supplier");
        }
        Map<UUID, PurchaseItem> received = new HashMap<>();
        purchase.getItems().forEach(item -> received.put(item.getId(), item));
        Set<UUID> requested = new HashSet<>();
        PurchaseReturn value = new PurchaseReturn();
        value.setTenantId(tenantId); value.setPurchase(purchase);
        value.setReturnNo("PRT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        value.setReason(req.reason().trim()); value.setResolution(req.resolution());
        value.setCreatedBy(principal().getId()); value.setCreatedAt(Instant.now());
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseReturnLineRequest line : req.items()) {
            if (!requested.add(line.purchaseItemId())) throw new BusinessException("DUPLICATE_RETURN_LINE", "Duplicate purchase return item");
            PurchaseItem item = received.get(line.purchaseItemId());
            if (item == null) throw new BusinessException("INVALID_RETURN_ITEM", "Item is not part of this purchase");
            BigDecimal alreadyReturned = returnItemRepository.returnedQuantity(item.getId());
            BigDecimal available = item.getQuantity().subtract(alreadyReturned);
            if (line.quantity().compareTo(available) > 0) {
                throw new BusinessException("RETURN_QUANTITY_EXCEEDED", "Only " + available.stripTrailingZeros().toPlainString() + " remains eligible for return: " + item.getProduct().getName());
            }
            BigDecimal returnAmount = item.getAmount().add(item.getGstAmount()).multiply(line.quantity())
                    .divide(item.getQuantity(), 8, RoundingMode.HALF_UP);
            PurchaseReturnItem returnItem = new PurchaseReturnItem();
            returnItem.setPurchaseReturn(value); returnItem.setPurchaseItem(item); returnItem.setProduct(item.getProduct());
            returnItem.setQuantity(line.quantity()); returnItem.setReturnAmount(MoneyUtils.roundHalfUp2(returnAmount));
            value.getItems().add(returnItem); total = total.add(returnItem.getReturnAmount());
        }
        value.setReturnTotal(MoneyUtils.roundHalfUp2(total));
        PurchaseReturn saved = returnRepository.save(value);
        for (PurchaseReturnItem item : saved.getItems()) {
            inventoryService.recordMovement(item.getProduct().getId(), MovementType.purchase_return, item.getQuantity(),
                    RefType.purchase_order, saved.getId(), saved.getReturnNo() + " · " + saved.getReason());
        }
        auditService.log("PURCHASE_RETURN_CREATED", "purchase_return", saved.getId(), Map.of(
                "purchaseNo", purchase.getPurchaseNo(), "returnNo", saved.getReturnNo(), "returnTotal", saved.getReturnTotal()));
        return response(saved);
    }

    @Transactional(readOnly = true)
    public List<PurchaseReturnResponse> list() {
        return returnRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.require()).stream().map(this::response).toList();
    }

    private PurchaseReturnResponse response(PurchaseReturn value) {
        Supplier supplier = supplierRepository.findById(value.getPurchase().getSupplierId()).orElse(null);
        return new PurchaseReturnResponse(value.getId(), value.getReturnNo(), value.getPurchase().getId(), value.getPurchase().getPurchaseNo(),
                supplier == null ? "Unknown supplier" : supplier.getName(), value.getReason(), value.getResolution(), value.getReturnTotal(), value.getCreatedAt(),
                value.getItems().stream().map(item -> new PurchaseReturnResponse.Line(item.getPurchaseItem().getId(), item.getProduct().getId(),
                        item.getProduct().getName(), item.getProduct().getSku(), item.getQuantity(), item.getReturnAmount())).toList());
    }
    private static UserPrincipal principal() {
        Object value = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (value instanceof UserPrincipal user) return user;
        throw new BusinessException("UNAUTHENTICATED", "User context required");
    }
}
