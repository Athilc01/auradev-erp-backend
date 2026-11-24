package com.auradev.erp.procurement.dto;
import com.auradev.erp.billing.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
public record RecordPurchasePaymentRequest(@NotNull @DecimalMin("0.01") BigDecimal amount, @NotNull PaymentMethod method, String notes) {}
