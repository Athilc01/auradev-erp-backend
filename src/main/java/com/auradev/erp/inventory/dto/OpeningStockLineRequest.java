package com.auradev.erp.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OpeningStockLineRequest(
        @NotNull UUID productId,
        @NotNull @Positive BigDecimal quantity,
        String batchNumber,
        LocalDate expiryDate,
        String notes
) {}
