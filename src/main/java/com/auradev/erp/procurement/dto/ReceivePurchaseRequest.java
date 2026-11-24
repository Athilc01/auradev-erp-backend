package com.auradev.erp.procurement.dto;
import jakarta.validation.Valid;
import java.util.List;
public record ReceivePurchaseRequest(List<@Valid ReceivePurchaseLineRequest> items, String notes) {}
