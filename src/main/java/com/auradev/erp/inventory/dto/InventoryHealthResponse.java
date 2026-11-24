package com.auradev.erp.inventory.dto;
import java.math.BigDecimal; import java.time.Instant; import java.time.LocalDate; import java.util.List; import java.util.UUID;
public record InventoryHealthResponse(List<StockRisk> slowMoving, List<ExpiryRisk> nearExpiry) {
 public record StockRisk(UUID productId, String name, String sku, BigDecimal stock, BigDecimal valueAtRisk, Instant lastSaleAt, String classification) {}
 public record ExpiryRisk(UUID productId, String name, String sku, String batchNumber, LocalDate expiryDate, BigDecimal quantity, BigDecimal valueAtRisk, long daysLeft) {}
}
