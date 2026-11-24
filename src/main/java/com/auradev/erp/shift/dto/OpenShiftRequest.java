package com.auradev.erp.shift.dto;
import jakarta.validation.constraints.*; import java.math.BigDecimal;
public record OpenShiftRequest(@NotNull @PositiveOrZero BigDecimal openingCash) {}
