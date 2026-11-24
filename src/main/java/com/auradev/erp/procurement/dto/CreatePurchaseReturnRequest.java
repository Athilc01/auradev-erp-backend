package com.auradev.erp.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record CreatePurchaseReturnRequest(
        @NotBlank String reason,
        @Pattern(regexp = "SUPPLIER_CREDIT|CASH_REFUND|BANK_REFUND") String resolution,
        @NotEmpty List<@Valid PurchaseReturnLineRequest> items) {}
