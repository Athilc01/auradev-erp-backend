package com.auradev.erp.audit.controller;

import com.auradev.erp.audit.dto.AuditLogResponse;
import com.auradev.erp.audit.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit", description = "Tenant audit log")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("@authz.can(authentication, 'AUDIT_VIEW')")
    @Operation(summary = "List audit events for the current tenant, optionally filtered by date")
    public ResponseEntity<List<AuditLogResponse>> list(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate) {
        if ((fromDate == null) != (toDate == null)) {
            throw new IllegalArgumentException("Choose both a start date and end date");
        }
        if (fromDate != null && toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("End date must not be before start date");
        }
        ZoneId zone = ZoneId.of("Asia/Qatar");
        Instant from = fromDate == null ? null : fromDate.atStartOfDay(zone).toInstant();
        Instant to = toDate == null ? null : toDate.plusDays(1).atStartOfDay(zone).toInstant();
        return ResponseEntity.ok(auditService.list(limit, from, to));
    }
}
