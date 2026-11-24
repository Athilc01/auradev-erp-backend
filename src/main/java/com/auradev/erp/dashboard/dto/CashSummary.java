package com.auradev.erp.dashboard.dto;

import java.math.BigDecimal;

/** Payments collected for the current store day. */
public record CashSummary(BigDecimal cash, BigDecimal upi, BigDecimal card, BigDecimal total) {}
