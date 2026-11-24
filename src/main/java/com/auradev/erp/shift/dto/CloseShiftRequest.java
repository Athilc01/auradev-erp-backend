package com.auradev.erp.shift.dto;
import jakarta.validation.constraints.*; import java.math.BigDecimal;
public record CloseShiftRequest(@NotNull @PositiveOrZero BigDecimal countedCash, String note) {}
