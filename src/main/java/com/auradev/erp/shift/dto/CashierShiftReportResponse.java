package com.auradev.erp.shift.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A manager-readable X (open) or Z (closed) shift reconciliation report. */
public record CashierShiftReportResponse(
        UUID id, String status, UUID cashierId, String cashierName,
        BigDecimal openingCash, Instant openedAt, Instant closedAt,
        BigDecimal expectedCash, BigDecimal expectedUpi, BigDecimal expectedCard,
        BigDecimal expectedCredit, BigDecimal countedCash, BigDecimal variance,
        String closingNote) {}
