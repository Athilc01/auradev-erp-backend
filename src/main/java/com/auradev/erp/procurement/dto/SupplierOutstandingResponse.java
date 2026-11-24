package com.auradev.erp.procurement.dto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public record SupplierOutstandingResponse(UUID supplierId, String supplierName, BigDecimal totalDue, List<Bill> bills) {
    public record Bill(UUID id, String purchaseNo, BigDecimal grandTotal, BigDecimal paid, BigDecimal balanceDue, String billDate) {}
}
