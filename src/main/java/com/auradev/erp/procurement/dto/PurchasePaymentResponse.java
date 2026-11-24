package com.auradev.erp.procurement.dto;
import com.auradev.erp.billing.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record PurchasePaymentResponse(UUID id, PaymentMethod method, BigDecimal amount, String notes, Instant createdAt) {}
