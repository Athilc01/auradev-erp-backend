package com.auradev.erp.procurement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Setter @Entity @Table(name = "purchase_returns")
public class PurchaseReturn {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_id") private Purchase purchase;
    @Column(name = "return_no", nullable = false) private String returnNo;
    @Column(nullable = false, columnDefinition = "text") private String reason;
    @Column(nullable = false) private String resolution;
    @Column(name = "return_total", nullable = false, precision = 12, scale = 2) private BigDecimal returnTotal;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @OneToMany(mappedBy = "purchaseReturn", cascade = CascadeType.ALL, orphanRemoval = true) private List<PurchaseReturnItem> items = new ArrayList<>();
}
