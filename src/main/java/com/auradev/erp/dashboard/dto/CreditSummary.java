package com.auradev.erp.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

/** Current unpaid customer credit across the tenant. */
public record CreditSummary(long customers, BigDecimal outstanding, List<CreditCustomer> topCustomers) {}
