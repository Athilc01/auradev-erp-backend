package com.auradev.erp.procurement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PurchaseReturnResponse(UUID id, String returnNo, UUID purchaseId, String purchaseNo,
                                     String supplierName, String reason, String resolution,
                                     BigDecimal returnTotal, Instant createdAt, List<Line> items) {
    public record Line(UUID purchaseItemId, UUID productId, String productName, String sku,
                       BigDecimal quantity, BigDecimal returnAmount) {}
}
