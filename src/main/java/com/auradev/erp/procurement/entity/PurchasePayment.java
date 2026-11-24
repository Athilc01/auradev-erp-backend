package com.auradev.erp.procurement.entity;

import com.auradev.erp.billing.entity.PaymentMethod;
import com.auradev.erp.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;

@Getter @Setter @Entity @Table(name = "purchase_payments")
public class PurchasePayment extends BaseEntity {
    @ManyToOne(optional = false) @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;
    @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, columnDefinition = "payment_method")
    private PaymentMethod method;
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Column(name = "notes") private String notes;
    @Column(name = "settlement_ref") private String settlementRef;
}
