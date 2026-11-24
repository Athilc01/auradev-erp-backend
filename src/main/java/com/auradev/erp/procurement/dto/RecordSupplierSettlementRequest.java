package com.auradev.erp.procurement.dto;
import com.auradev.erp.billing.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public record RecordSupplierSettlementRequest(@NotNull @DecimalMin("0.01") BigDecimal amount, @NotNull PaymentMethod method, String notes, @NotEmpty List<@Valid Allocation> allocations) {
    public record Allocation(@NotNull UUID purchaseId, @NotNull @DecimalMin("0.01") BigDecimal amount) {}
}
