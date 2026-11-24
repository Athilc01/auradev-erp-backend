package com.auradev.erp.dashboard.dto;

import java.util.List;

public record DashboardOperations(
        CashSummary cashSummary,
        CreditSummary creditSummary,
        List<DashboardAction> actions
) {}
