package com.auradev.erp.procurement.dto;

import java.math.BigDecimal;

/**
 * Compact procurement dashboard totals. Kept separate from the paged purchase
 * list so dashboards do not need to download every purchase just to show KPIs.
 */
public record PurchaseKpiResponse(
        BigDecimal totalValue,
        BigDecimal awaitingValue,
        long awaitingCount,
        BigDecimal paidValue,
        long totalOrders,
        long draftCount,
        long pendingCount,
        long completedCount,
        long cancelledCount) {
}
