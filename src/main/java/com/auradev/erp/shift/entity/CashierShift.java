package com.auradev.erp.shift.entity;
import com.auradev.erp.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
@Getter @Setter @Entity @Table(name = "cashier_shifts")
public class CashierShift extends BaseEntity {
  @Column(name="cashier_id", nullable=false) private UUID cashierId;
  @Column(nullable=false) private String status;
  @Column(name="opening_cash", nullable=false, precision=12, scale=2) private BigDecimal openingCash;
  @Column(name="opened_at", nullable=false) private Instant openedAt;
  @Column(name="closed_at") private Instant closedAt;
  @Column(name="expected_cash", precision=12, scale=2) private BigDecimal expectedCash;
  @Column(name="expected_upi", precision=12, scale=2) private BigDecimal expectedUpi;
  @Column(name="expected_card", precision=12, scale=2) private BigDecimal expectedCard;
  @Column(name="expected_credit", precision=12, scale=2) private BigDecimal expectedCredit;
  @Column(name="counted_cash", precision=12, scale=2) private BigDecimal countedCash;
  @Column(precision=12, scale=2) private BigDecimal variance;
  @Column(name="closing_note") private String closingNote;
}
