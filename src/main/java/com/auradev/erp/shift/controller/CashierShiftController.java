package com.auradev.erp.shift.controller;
import com.auradev.erp.shift.dto.*; import com.auradev.erp.shift.service.CashierShiftService;
import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.http.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.UUID;
@RestController @RequestMapping("/api/v1/cashier-shifts") @RequiredArgsConstructor
public class CashierShiftController { private final CashierShiftService shifts;
 @GetMapping("/current") @PreAuthorize("@authz.can(authentication, 'BILL_CREATE')") public CashierShiftResponse current(){return shifts.current();}
 @GetMapping("/history") @PreAuthorize("@authz.can(authentication, 'AUDIT_VIEW')") public List<CashierShiftReportResponse> history(@RequestParam(defaultValue="100") int limit){return shifts.history(limit);}
 @GetMapping("/history/{id}") @PreAuthorize("@authz.can(authentication, 'AUDIT_VIEW')") public CashierShiftReportResponse report(@PathVariable UUID id){return shifts.report(id);}
 @PostMapping("/open") @PreAuthorize("@authz.can(authentication, 'BILL_CREATE')") public ResponseEntity<CashierShiftResponse> open(@Valid @RequestBody OpenShiftRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(shifts.open(r));}
 @PostMapping("/close") @PreAuthorize("@authz.can(authentication, 'BILL_CREATE')") public CashierShiftResponse close(@Valid @RequestBody CloseShiftRequest r){return shifts.close(r);}
 @PostMapping("/history/{id}/force-close") @PreAuthorize("@authz.can(authentication, 'AUDIT_VIEW')") public CashierShiftResponse forceClose(@PathVariable UUID id,@Valid @RequestBody CloseShiftRequest r){return shifts.forceClose(id,r);}
}
