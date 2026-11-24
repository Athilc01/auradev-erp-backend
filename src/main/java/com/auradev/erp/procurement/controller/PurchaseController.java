package com.auradev.erp.procurement.controller;

import com.auradev.erp.common.pagination.PageResponse;
import com.auradev.erp.procurement.dto.CreatePurchaseRequest;
import com.auradev.erp.procurement.dto.CancelPurchaseRequest;
import com.auradev.erp.procurement.dto.CreatePurchaseReturnRequest;
import com.auradev.erp.procurement.dto.PurchaseReturnResponse;
import com.auradev.erp.procurement.dto.ReceivePurchaseRequest;
import com.auradev.erp.procurement.dto.PurchaseReceiptResponse;
import com.auradev.erp.procurement.dto.PurchaseResponse;
import com.auradev.erp.procurement.dto.PurchaseSummaryResponse;
import com.auradev.erp.procurement.dto.PurchaseKpiResponse;
import com.auradev.erp.procurement.dto.RecordPurchasePaymentRequest;
import com.auradev.erp.procurement.dto.RecordSupplierSettlementRequest;
import com.auradev.erp.procurement.dto.SupplierOutstandingResponse;
import com.auradev.erp.procurement.dto.SupplierPaymentHistoryResponse;
import com.auradev.erp.procurement.service.PurchaseService;
import com.auradev.erp.procurement.service.PurchaseReturnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchases")
@Tag(name = "Purchases", description = "Supplier purchase bills and GRN")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final PurchaseReturnService purchaseReturnService;

    @GetMapping
    @PreAuthorize("@authz.can(authentication, 'PURCHASE_VIEW')")
    public ResponseEntity<PageResponse<PurchaseSummaryResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID supplierId,
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(purchaseService.list(q, status, supplierId, pageable));
    }

    @GetMapping("/kpis")
    @PreAuthorize("@authz.can(authentication, 'PURCHASE_VIEW')")
    public ResponseEntity<PurchaseKpiResponse> kpis() {
        return ResponseEntity.ok(purchaseService.kpis());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.can(authentication, 'PURCHASE_VIEW')")
    public ResponseEntity<PurchaseResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(purchaseService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authz.can(authentication, 'PURCHASE_MANAGE')")
    public ResponseEntity<PurchaseResponse> create(@Valid @RequestBody CreatePurchaseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseService.create(req));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("@authz.can(authentication, 'PURCHASE_MANAGE')")
    public ResponseEntity<PurchaseResponse> confirm(@PathVariable UUID id) {
        return ResponseEntity.ok(purchaseService.confirm(id));
    }

    @PostMapping("/{id}/receive")
    @Operation(summary = "Receive GRN", description = "Stock in all lines and mark purchase as billed")
    @PreAuthorize("@authz.can(authentication, 'PURCHASE_MANAGE')")
    public ResponseEntity<PurchaseResponse> receive(@PathVariable UUID id, @Valid @RequestBody(required = false) ReceivePurchaseRequest req) {
        return ResponseEntity.ok(purchaseService.receive(id, req));
    }

    @GetMapping("/{id}/receipts")
    @PreAuthorize("@authz.can(authentication, 'PURCHASE_VIEW')")
    public ResponseEntity<java.util.List<PurchaseReceiptResponse>> receipts(@PathVariable UUID id) {
        return ResponseEntity.ok(purchaseService.receipts(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a confirmed purchase before GRN", description = "Cancels an order awaiting delivery. No stock is changed.")
    @PreAuthorize("@authz.can(authentication, 'PURCHASE_MANAGE')")
    public ResponseEntity<PurchaseResponse> cancel(@PathVariable UUID id, @Valid @RequestBody CancelPurchaseRequest req) {
        return ResponseEntity.ok(purchaseService.cancel(id, req));
    }

    @PostMapping("/{id}/returns")
    @PreAuthorize("@authz.can(authentication, 'PURCHASE_MANAGE')")
    public ResponseEntity<PurchaseReturnResponse> createReturn(@PathVariable UUID id, @Valid @RequestBody CreatePurchaseReturnRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseReturnService.create(id, req));
    }

    @GetMapping("/returns")
    @PreAuthorize("@authz.can(authentication, 'PURCHASE_VIEW')")
    public ResponseEntity<java.util.List<PurchaseReturnResponse>> listReturns() {
        return ResponseEntity.ok(purchaseReturnService.list());
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("@authz.can(authentication, 'PURCHASE_MANAGE')")
    public ResponseEntity<PurchaseResponse> markPaid(@PathVariable UUID id) {
        return ResponseEntity.ok(purchaseService.markPaid(id));
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("@authz.can(authentication, 'PURCHASE_MANAGE')")
    @Operation(summary = "Record a full or partial supplier payment")
    public ResponseEntity<PurchaseResponse> recordPayment(@PathVariable UUID id, @Valid @RequestBody RecordPurchasePaymentRequest req) {
        return ResponseEntity.ok(purchaseService.recordPayment(id, req));
    }

    @GetMapping("/supplier/{supplierId}/outstanding")
    @PreAuthorize("@authz.can(authentication, 'PURCHASE_VIEW')")
    public ResponseEntity<SupplierOutstandingResponse> outstanding(@PathVariable UUID supplierId) {
        return ResponseEntity.ok(purchaseService.supplierOutstanding(supplierId));
    }

    @GetMapping("/supplier/{supplierId}/payment-history")
    @PreAuthorize("@authz.can(authentication, 'PURCHASE_VIEW')")
    public ResponseEntity<SupplierPaymentHistoryResponse> paymentHistory(@PathVariable UUID supplierId) {
        return ResponseEntity.ok(purchaseService.supplierPaymentHistory(supplierId));
    }

    @PostMapping("/supplier/{supplierId}/settlements")
    @PreAuthorize("@authz.can(authentication, 'PURCHASE_MANAGE')")
    public ResponseEntity<SupplierOutstandingResponse> settleSupplier(@PathVariable UUID supplierId, @Valid @RequestBody RecordSupplierSettlementRequest req) {
        return ResponseEntity.ok(purchaseService.recordSupplierSettlement(supplierId, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authz.can(authentication, 'PURCHASE_MANAGE')")
    public ResponseEntity<Void> deleteDraft(@PathVariable UUID id) {
        purchaseService.deleteDraft(id);
        return ResponseEntity.noContent().build();
    }
}
