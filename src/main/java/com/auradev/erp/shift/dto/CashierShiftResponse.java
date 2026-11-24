package com.auradev.erp.shift.dto;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record CashierShiftResponse(UUID id, String status, BigDecimal openingCash, Instant openedAt, Instant closedAt, BigDecimal expectedCash, BigDecimal expectedUpi, BigDecimal expectedCard, BigDecimal expectedCredit, BigDecimal countedCash, BigDecimal variance, String closingNote) {}
