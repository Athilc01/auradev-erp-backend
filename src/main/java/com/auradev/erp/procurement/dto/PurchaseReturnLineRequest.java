package com.auradev.erp.procurement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseReturnLineRequest(@NotNull UUID purchaseItemId, @NotNull @DecimalMin("0.001") BigDecimal quantity) {}
