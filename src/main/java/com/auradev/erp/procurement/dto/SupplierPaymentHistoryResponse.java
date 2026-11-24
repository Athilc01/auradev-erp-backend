package com.auradev.erp.procurement.dto;
import com.auradev.erp.billing.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record SupplierPaymentHistoryResponse(UUID supplierId, String supplierName, List<Settlement> settlements) {
    public record Settlement(String reference, PaymentMethod method, BigDecimal amount, String notes, Instant createdAt, List<Allocation> allocations) {}
    public record Allocation(UUID purchaseId, String purchaseNo, BigDecimal amount) {}
}
