package com.auradev.erp.shift.repository;
import com.auradev.erp.shift.entity.CashierShift;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CashierShiftRepository extends JpaRepository<CashierShift, UUID> {
  Optional<CashierShift> findByTenantIdAndCashierIdAndStatus(UUID tenantId, UUID cashierId, String status);
  Optional<CashierShift> findFirstByTenantIdAndCashierIdAndStatusInOrderByOpenedAtDesc(UUID tenantId, UUID cashierId, Collection<String> statuses);
  List<CashierShift> findByTenantIdOrderByOpenedAtDesc(UUID tenantId);
  List<CashierShift> findByStatus(String status);
}
