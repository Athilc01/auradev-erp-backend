package com.auradev.erp.procurement.entity;

import com.auradev.erp.catalog.entity.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @Entity @Table(name = "purchase_receipt_items")
public class PurchaseReceiptItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "receipt_id") private PurchaseReceipt receipt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_item_id") private PurchaseItem purchaseItem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id") private Product product;
    @Column(nullable = false, precision = 12, scale = 3) private BigDecimal quantity;
    @Column(name = "product_name_snapshot") private String productNameSnapshot;
    @Column(name = "sku_snapshot") private String skuSnapshot;
    @Column(name = "unit_label_snapshot") private String unitLabelSnapshot;
    @Column(name = "ordered_quantity", precision = 12, scale = 3) private BigDecimal orderedQuantity;
    @Column(name = "batch_number") private String batchNumber;
    @Column(name = "expiry_date") private java.time.LocalDate expiryDate;
}
