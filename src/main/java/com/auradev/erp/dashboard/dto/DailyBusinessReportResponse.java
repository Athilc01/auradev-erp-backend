package com.auradev.erp.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Manager-facing close-of-day totals for one business date. */
public record DailyBusinessReportResponse(
        LocalDate businessDate,
        BigDecimal sales,
        long bills,
        BigDecimal itemsSold,
        BigDecimal cash,
        BigDecimal upi,
        BigDecimal card,
        BigDecimal creditSales,
        long returnCount,
        BigDecimal refundTotal,
        BigDecimal purchaseValue,
        BigDecimal purchasePayments,
        int lowStockCount,
        int expiringBatchCount,
        long closedShifts,
        BigDecimal shiftVariance
) {}
