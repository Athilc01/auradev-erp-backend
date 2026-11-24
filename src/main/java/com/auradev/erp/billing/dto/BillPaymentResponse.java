package com.auradev.erp.billing.dto;

import com.auradev.erp.billing.entity.PaymentMethod;

import java.math.BigDecimal;

/** A single payment component on a completed sale, including split payments. */
public record BillPaymentResponse(
        PaymentMethod method,
        BigDecimal amount,
        BigDecimal tendered,
        BigDecimal changeDue
) {}
