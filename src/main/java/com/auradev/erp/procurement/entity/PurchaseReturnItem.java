package com.auradev.erp.procurement.entity;

import com.auradev.erp.catalog.entity.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @Entity @Table(name = "purchase_return_items")
public class PurchaseReturnItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "return_id") private PurchaseReturn purchaseReturn;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_item_id") private PurchaseItem purchaseItem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id") private Product product;
    @Column(nullable = false, precision = 12, scale = 3) private BigDecimal quantity;
    @Column(name = "return_amount", nullable = false, precision = 12, scale = 2) private BigDecimal returnAmount;
}
