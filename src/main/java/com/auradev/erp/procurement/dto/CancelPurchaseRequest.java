package com.auradev.erp.procurement.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelPurchaseRequest(@NotBlank(message = "Cancellation reason is required") String reason) {}
