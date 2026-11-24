package com.auradev.erp.dashboard.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreditCustomer(UUID id, String name, String phone, BigDecimal outstanding) {}
