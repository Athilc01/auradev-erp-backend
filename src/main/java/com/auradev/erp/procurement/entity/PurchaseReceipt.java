package com.auradev.erp.procurement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Setter @Entity @Table(name = "purchase_receipts")
public class PurchaseReceipt {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_id") private Purchase purchase;
    @Column(name = "grn_no", nullable = false) private String grnNo;
    @Column(name = "purchase_no_snapshot") private String purchaseNoSnapshot;
    @Column(name = "supplier_name_snapshot") private String supplierNameSnapshot;
    @Column(name = "receiver_name_snapshot") private String receiverNameSnapshot;
    @Column(columnDefinition = "text") private String notes;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true) private List<PurchaseReceiptItem> items = new ArrayList<>();
}
