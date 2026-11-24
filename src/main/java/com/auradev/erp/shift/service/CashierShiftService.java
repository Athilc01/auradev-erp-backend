package com.auradev.erp.shift.service;
import com.auradev.erp.auth.security.UserPrincipal;
import com.auradev.erp.audit.AuditLogEvent;
import com.auradev.erp.common.error.BusinessException;
import com.auradev.erp.settings.entity.TenantSettings;
import com.auradev.erp.settings.model.BillingConfig;
import com.auradev.erp.settings.repository.TenantSettingsRepository;
import com.auradev.erp.shift.dto.*;
import com.auradev.erp.shift.entity.CashierShift;
import com.auradev.erp.shift.repository.CashierShiftRepository;
import com.auradev.erp.tenant.TenantContext;
import com.auradev.erp.user.entity.User;
import com.auradev.erp.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal; import java.time.*; import java.util.*;
@Service @RequiredArgsConstructor @Transactional
public class CashierShiftService {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Qatar");
  private final CashierShiftRepository shifts; private final JdbcTemplate jdbc; private final UserRepository users; private final TenantSettingsRepository tenantSettings; private final ApplicationEventPublisher events;
  public CashierShiftResponse current() { return shifts.findFirstByTenantIdAndCashierIdAndStatusInOrderByOpenedAtDesc(TenantContext.require(), userId(), List.of("OPEN", "OVERDUE")).map(this::mapOpenShift).orElse(null); }
  public CashierShiftResponse open(OpenShiftRequest req) {
    UUID tenant=TenantContext.require(), user=userId();
    if (shifts.findFirstByTenantIdAndCashierIdAndStatusInOrderByOpenedAtDesc(tenant,user,List.of("OPEN","OVERDUE")).isPresent()) throw new BusinessException("SHIFT_ALREADY_OPEN","Close or reconcile your current cashier shift before opening another");
    CashierShift s=new CashierShift(); s.setTenantId(tenant); s.setCashierId(user); s.setStatus("OPEN"); s.setOpeningCash(req.openingCash()); s.setOpenedAt(Instant.now()); s.setCreatedBy(user); s.setUpdatedBy(user); return map(shifts.save(s));
  }
  public void requireOpenShift() { CashierShiftResponse shift=current(); if (shift==null) throw new BusinessException("SHIFT_REQUIRED","Open your cashier shift before completing a sale"); if (!"OPEN".equals(shift.status())) throw new BusinessException("SHIFT_OVERDUE","Your cashier shift is overdue. Reconcile and close it before billing."); }
  public CashierShiftResponse close(CloseShiftRequest req) {
    CashierShift s=shifts.findFirstByTenantIdAndCashierIdAndStatusInOrderByOpenedAtDesc(TenantContext.require(),userId(),List.of("OPEN","OVERDUE")).orElseThrow(() -> new BusinessException("NO_OPEN_SHIFT","There is no open cashier shift to close"));
    return closeShift(s, req, userId(), false);
  }
  public CashierShiftResponse forceClose(UUID id, CloseShiftRequest req) {
    CashierShift s=shifts.findById(id).filter(x -> x.getTenantId().equals(TenantContext.require())).filter(x -> "OPEN".equals(x.getStatus()) || "OVERDUE".equals(x.getStatus())).orElseThrow(() -> new BusinessException("SHIFT_NOT_FOUND","Open or overdue shift not found"));
    if (req.note()==null || req.note().isBlank()) throw new BusinessException("CLOSE_REASON_REQUIRED","A manager close reason is required");
    CashierShiftResponse result=closeShift(s,req,userId(),true);
    events.publishEvent(new AuditLogEvent(s.getTenantId(), userId(), "CASHIER_SHIFT_MANAGER_CLOSED", "cashier_shift", s.getId(), Map.of("cashierId",s.getCashierId().toString(),"reason",result.closingNote(),"variance",String.valueOf(result.variance())), null));
    return result;
  }
  private CashierShiftResponse closeShift(CashierShift s, CloseShiftRequest req, UUID closedBy, boolean managerClose) {
    BigDecimal[] totals=totals(s); BigDecimal expectedCash=s.getOpeningCash().add(totals[0]);
    s.setExpectedCash(expectedCash); s.setExpectedUpi(totals[1]); s.setExpectedCard(totals[2]); s.setExpectedCredit(totals[3]); s.setCountedCash(req.countedCash()); s.setVariance(req.countedCash().subtract(expectedCash)); s.setClosingNote((managerClose?"Manager close: ":"")+(req.note()==null||req.note().isBlank()?"":req.note().trim())); s.setClosedAt(Instant.now()); s.setStatus("CLOSED"); s.setUpdatedBy(closedBy); return map(s);
  }
  @Scheduled(cron = "0 */5 * * * *")
  public void markMissedShiftsOverdue() {
    Instant now=Instant.now();
    for (CashierShift s: shifts.findByStatus("OPEN")) {
      BillingConfig config=tenantSettings.findById(s.getTenantId()).map(TenantSettings::getBilling).orElse(BillingConfig.defaults()).normalized();
      if (!config.markOverdueShiftsAtClose() || !now.isAfter(cutoff(s.getOpenedAt(), config.shiftClosingTime()))) continue;
      s.setStatus("OVERDUE"); s.setUpdatedBy(null); shifts.save(s);
      events.publishEvent(new AuditLogEvent(s.getTenantId(), null, "CASHIER_SHIFT_OVERDUE", "cashier_shift", s.getId(), Map.of("cutoff",config.shiftClosingTime(),"cashierId",s.getCashierId().toString()), null));
    }
  }
  private Instant cutoff(Instant openedAt, String value) {
    LocalTime close=LocalTime.parse(value); ZonedDateTime opened=openedAt.atZone(BUSINESS_ZONE); ZonedDateTime cutoff=opened.toLocalDate().atTime(close).atZone(BUSINESS_ZONE);
    if (!opened.toLocalTime().isBefore(close)) cutoff=cutoff.plusDays(1);
    return cutoff.toInstant();
  }
  @Transactional(readOnly = true)
  public List<CashierShiftReportResponse> history(int limit) {
    List<CashierShift> rows = shifts.findByTenantIdOrderByOpenedAtDesc(TenantContext.require());
    Map<UUID, String> names = users.findAllById(rows.stream().map(CashierShift::getCashierId).collect(java.util.stream.Collectors.toSet())).stream()
        .collect(java.util.stream.Collectors.toMap(User::getId, User::getName));
    return rows.stream().limit(Math.min(Math.max(limit, 1), 100)).map(s -> report(s, names.getOrDefault(s.getCashierId(), "Unknown cashier"))).toList();
  }
  @Transactional(readOnly = true)
  public CashierShiftReportResponse report(UUID id) {
    CashierShift s = shifts.findById(id).filter(x -> x.getTenantId().equals(TenantContext.require()))
        .orElseThrow(() -> new BusinessException("SHIFT_NOT_FOUND", "Shift not found"));
    return report(s, users.findById(s.getCashierId()).map(User::getName).orElse("Unknown cashier"));
  }
  private BigDecimal[] totals(CashierShift s) { return jdbc.queryForObject(
      "SELECT COALESCE(SUM(CASE WHEN p.method='CASH' THEN p.amount ELSE 0 END),0), " +
      "COALESCE(SUM(CASE WHEN p.method='UPI' THEN p.amount ELSE 0 END),0), " +
      "COALESCE(SUM(CASE WHEN p.method='CARD' THEN p.amount ELSE 0 END),0), " +
      "COALESCE(SUM(CASE WHEN p.method='CREDIT' THEN p.amount ELSE 0 END),0) " +
      "FROM payments p JOIN bills b ON b.id=p.bill_id WHERE b.tenant_id=? AND b.cashier_id=? " +
      "AND b.status='COMPLETED' AND b.created_at>=?",
      (rs,n)->new BigDecimal[]{rs.getBigDecimal(1),rs.getBigDecimal(2),rs.getBigDecimal(3),rs.getBigDecimal(4)}, s.getTenantId(),s.getCashierId(),java.sql.Timestamp.from(s.getOpenedAt())); }
  private CashierShiftResponse mapOpenShift(CashierShift s) {
    BigDecimal[] totals = totals(s);
    return new CashierShiftResponse(s.getId(), s.getStatus(), s.getOpeningCash(), s.getOpenedAt(), s.getClosedAt(), s.getOpeningCash().add(totals[0]), totals[1], totals[2], totals[3], s.getCountedCash(), s.getVariance(), s.getClosingNote());
  }
  private CashierShiftReportResponse report(CashierShift s, String cashierName) {
    BigDecimal expectedCash = s.getExpectedCash(), upi = s.getExpectedUpi(), card = s.getExpectedCard(), credit = s.getExpectedCredit();
    if ("OPEN".equals(s.getStatus())) { BigDecimal[] totals = totals(s); expectedCash = s.getOpeningCash().add(totals[0]); upi = totals[1]; card = totals[2]; credit = totals[3]; }
    return new CashierShiftReportResponse(s.getId(), s.getStatus(), s.getCashierId(), cashierName, s.getOpeningCash(), s.getOpenedAt(), s.getClosedAt(), expectedCash, upi, card, credit, s.getCountedCash(), s.getVariance(), s.getClosingNote());
  }
  private UUID userId(){var a=SecurityContextHolder.getContext().getAuthentication(); if(a!=null&&a.getPrincipal() instanceof UserPrincipal p)return p.getId(); throw new BusinessException("AUTH_REQUIRED","Sign in to manage a shift");}
  private CashierShiftResponse map(CashierShift s){return new CashierShiftResponse(s.getId(),s.getStatus(),s.getOpeningCash(),s.getOpenedAt(),s.getClosedAt(),s.getExpectedCash(),s.getExpectedUpi(),s.getExpectedCard(),s.getExpectedCredit(),s.getCountedCash(),s.getVariance(),s.getClosingNote());}
}
