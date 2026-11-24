package com.auradev.erp.procurement.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record PurchaseReceiptResponse(UUID id, String grnNo, String purchaseNo, String supplierName, String receivedBy, String notes, Instant createdAt, List<Line> items) {
    public record Line(UUID purchaseItemId, String productName, String sku, String unitLabel, BigDecimal orderedQuantity, BigDecimal quantity, String batchNumber, java.time.LocalDate expiryDate) {}
}
