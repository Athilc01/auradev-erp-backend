package com.auradev.erp.procurement.service;

import com.auradev.erp.auth.security.UserPrincipal;
import com.auradev.erp.audit.service.AuditService;
import com.auradev.erp.billing.entity.PaymentMethod;
import com.auradev.erp.catalog.entity.Product;
import com.auradev.erp.catalog.entity.Supplier;
import com.auradev.erp.catalog.repository.ProductRepository;
import com.auradev.erp.catalog.repository.SupplierRepository;
import com.auradev.erp.common.error.BusinessException;
import com.auradev.erp.common.error.EntityNotFoundException;
import com.auradev.erp.common.pagination.PageResponse;
import com.auradev.erp.common.util.MoneyUtils;
import com.auradev.erp.inventory.entity.MovementType;
import com.auradev.erp.inventory.entity.RefType;
import com.auradev.erp.inventory.service.InventoryService;
import com.auradev.erp.procurement.dto.*;
import com.auradev.erp.procurement.entity.*;
import com.auradev.erp.procurement.repository.PurchaseRepository;
import com.auradev.erp.procurement.repository.PurchaseSequenceRepository;
import com.auradev.erp.procurement.repository.PurchasePaymentRepository;
import com.auradev.erp.procurement.repository.PurchaseReceiptRepository;
import com.auradev.erp.procurement.repository.PurchaseReceiptItemRepository;
import com.auradev.erp.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class PurchaseService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final PurchaseRepository purchaseRepository;
    private final PurchaseSequenceRepository sequenceRepository;
    private final PurchasePaymentRepository purchasePaymentRepository;
    private final PurchaseReceiptRepository purchaseReceiptRepository;
    private final PurchaseReceiptItemRepository purchaseReceiptItemRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<PurchaseSummaryResponse> list(
            String q, String status, UUID supplierId, Pageable pageable) {
        UUID tenantId = TenantContext.require();
        List<PurchaseStatus> statusFilter = parseStatuses(status);
        var page = purchaseRepository.searchSummaries(
                tenantId, blankToNull(q), statusFilter, supplierId, pageable);
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public PurchaseKpiResponse kpis() {
        UUID tenantId = TenantContext.require();
        long billed = purchaseRepository.countByTenantIdAndStatus(tenantId, PurchaseStatus.BILLED);
        long paid = purchaseRepository.countByTenantIdAndStatus(tenantId, PurchaseStatus.PAID);
        return new PurchaseKpiResponse(
                zeroIfNull(purchaseRepository.sumGrandTotalByTenantId(tenantId)),
                zeroIfNull(purchaseRepository.sumGrandTotalByTenantIdAndStatus(tenantId, PurchaseStatus.BILLED)),
                billed,
                zeroIfNull(purchaseRepository.sumGrandTotalByTenantIdAndStatus(tenantId, PurchaseStatus.PAID)),
                purchaseRepository.countByTenantId(tenantId),
                purchaseRepository.countByTenantIdAndStatus(tenantId, PurchaseStatus.DRAFT),
                purchaseRepository.countByTenantIdAndStatus(tenantId, PurchaseStatus.PENDING_GRN),
                billed + paid,
                purchaseRepository.countByTenantIdAndStatus(tenantId, PurchaseStatus.CANCELLED));
    }

    @Transactional(readOnly = true)
    public PurchaseResponse get(UUID id) {
        UUID tenantId = TenantContext.require();
        Purchase purchase = purchaseRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase", id));
        Supplier supplier = supplierRepository.findById(purchase.getSupplierId())
                .orElseThrow(() -> new EntityNotFoundException("Supplier", purchase.getSupplierId()));
        return toResponse(purchase, supplier);
    }

    public PurchaseResponse create(CreatePurchaseRequest req) {
        UUID tenantId = TenantContext.require();
        UUID userId = requirePrincipal().getId();

        Supplier supplier = supplierRepository.findById(req.supplierId())
                .orElseThrow(() -> new EntityNotFoundException("Supplier", req.supplierId()));
        if (!supplier.isActive()) {
            throw new BusinessException("SUPPLIER_INACTIVE", "Supplier is inactive: " + supplier.getName());
        }

        List<ComputedLine> lines = computeLines(req.items());
        Totals totals = sumTotals(lines);

        Purchase purchase = new Purchase();
        purchase.setTenantId(tenantId);
        purchase.setPurchaseNo(nextPurchaseNo(tenantId));
        purchase.setSupplierId(supplier.getId());
        purchase.setBillDate(req.billDate());
        purchase.setDueDate(req.dueDate());
        purchase.setSubtotal(totals.subtotal());
        purchase.setGstTotal(totals.gstTotal());
        purchase.setGrandTotal(totals.grandTotal());
        purchase.setStatus(PurchaseStatus.DRAFT);
        purchase.setNotes(req.notes());
        purchase.setCreatedBy(userId);
        purchase.setUpdatedBy(userId);

        for (ComputedLine cl : lines) {
            purchase.getItems().add(toItem(purchase, cl));
        }

        Purchase saved = purchaseRepository.save(purchase);
        return toResponse(saved, supplier);
    }

    public PurchaseResponse confirm(UUID id) {
        Purchase purchase = loadDraftOrPending(id);
        if (purchase.getStatus() != PurchaseStatus.DRAFT) {
            throw new BusinessException("INVALID_STATUS", "Only draft purchases can be confirmed");
        }
        purchase.setStatus(PurchaseStatus.PENDING_GRN);
        purchase.setUpdatedBy(requirePrincipal().getId());
        return toResponse(purchaseRepository.save(purchase), loadSupplier(purchase.getSupplierId()));
    }

    public PurchaseResponse receive(UUID id, ReceivePurchaseRequest req) {
        UUID tenantId = TenantContext.require();
        Purchase purchase = purchaseRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase", id));

        if (purchase.getStatus() != PurchaseStatus.PENDING_GRN) {
            throw new BusinessException("INVALID_STATUS", "Only confirmed purchase orders can be received");
        }

        Map<UUID, PurchaseItem> ordered = new HashMap<>();
        purchase.getItems().forEach(item -> ordered.put(item.getId(), item));
        List<ReceivePurchaseLineRequest> requested = req == null || req.items() == null || req.items().isEmpty()
                ? purchase.getItems().stream().map(item -> new ReceivePurchaseLineRequest(item.getId(), item.getQuantity().subtract(receivedQuantity(item)), null, null))
                    .filter(line -> line.quantity().signum() > 0).toList()
                : req.items();
        if (requested.isEmpty()) throw new BusinessException("GRN_EMPTY", "Add at least one delivered quantity");
        Set<UUID> seen = new java.util.HashSet<>();
        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.setTenantId(tenantId); receipt.setPurchase(purchase);
        receipt.setGrnNo("GRN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        UserPrincipal receiver = requirePrincipal();
        Supplier supplier = loadSupplier(purchase.getSupplierId());
        receipt.setPurchaseNoSnapshot(purchase.getPurchaseNo());
        receipt.setSupplierNameSnapshot(supplier.getName());
        receipt.setReceiverNameSnapshot(receiver.getName());
        receipt.setNotes(req == null ? null : blankToNull(req.notes())); receipt.setCreatedBy(receiver.getId()); receipt.setCreatedAt(Instant.now());
        for (ReceivePurchaseLineRequest line : requested) {
            if (!seen.add(line.purchaseItemId())) throw new BusinessException("DUPLICATE_GRN_LINE", "Duplicate delivered item");
            PurchaseItem item = ordered.get(line.purchaseItemId());
            if (item == null) throw new BusinessException("INVALID_GRN_ITEM", "Item is not part of this purchase");
            if (line.quantity().signum() <= 0) throw new BusinessException("INVALID_QUANTITY", "Received quantity must be positive");
            BigDecimal remaining = item.getQuantity().subtract(receivedQuantity(item));
            if (line.quantity().compareTo(remaining) > 0) throw new BusinessException("RECEIPT_QUANTITY_EXCEEDED", "Only " + remaining.stripTrailingZeros().toPlainString() + " remains to receive for " + item.getProduct().getName());
            PurchaseReceiptItem receiptItem = new PurchaseReceiptItem();
            receiptItem.setReceipt(receipt); receiptItem.setPurchaseItem(item); receiptItem.setProduct(item.getProduct()); receiptItem.setQuantity(line.quantity());
            receiptItem.setProductNameSnapshot(item.getProduct().getName()); receiptItem.setSkuSnapshot(item.getProduct().getSku());
            receiptItem.setUnitLabelSnapshot(item.getProduct().getUnitLabel()); receiptItem.setOrderedQuantity(item.getQuantity());
            receiptItem.setBatchNumber(blankToNull(line.batchNumber())); receiptItem.setExpiryDate(line.expiryDate());
            receipt.getItems().add(receiptItem);
        }
        PurchaseReceipt savedReceipt = purchaseReceiptRepository.save(receipt);
        for (PurchaseReceiptItem receiptItem : savedReceipt.getItems()) {
            PurchaseItem item = receiptItem.getPurchaseItem();
            inventoryService.recordMovement(
                    item.getProduct().getId(),
                    MovementType.purchase,
                    receiptItem.getQuantity(),
                    RefType.purchase_order,
                    savedReceipt.getId(),
                    savedReceipt.getGrnNo() + " · " + purchase.getPurchaseNo());
            inventoryService.receivePurchaseBatch(item.getProduct().getId(), requested.stream().filter(line -> line.purchaseItemId().equals(item.getId())).findFirst().orElseThrow().batchNumber(), requested.stream().filter(line -> line.purchaseItemId().equals(item.getId())).findFirst().orElseThrow().expiryDate(), receiptItem.getQuantity(), savedReceipt.getNotes());

            Product product = item.getProduct();
            product.setCostPrice(item.getRate());
            productRepository.save(product);
        }

        boolean complete = purchase.getItems().stream().allMatch(item -> receivedQuantity(item).compareTo(item.getQuantity()) >= 0);
        purchase.setStatus(complete ? PurchaseStatus.BILLED : PurchaseStatus.PENDING_GRN);
        purchase.setUpdatedBy(requirePrincipal().getId());
        return toResponse(purchaseRepository.save(purchase), loadSupplier(purchase.getSupplierId()));
    }

    @Transactional(readOnly = true)
    public List<PurchaseReceiptResponse> receipts(UUID id) {
        UUID tenantId = TenantContext.require();
        purchaseRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new EntityNotFoundException("Purchase", id));
        return purchaseReceiptRepository.findByTenantIdAndPurchaseIdOrderByCreatedAtDesc(tenantId, id).stream()
                .map(receipt -> new PurchaseReceiptResponse(receipt.getId(), receipt.getGrnNo(), receipt.getPurchaseNoSnapshot(), receipt.getSupplierNameSnapshot(), receipt.getReceiverNameSnapshot(), receipt.getNotes(), receipt.getCreatedAt(),
                        receipt.getItems().stream().map(item -> new PurchaseReceiptResponse.Line(item.getPurchaseItem().getId(), item.getProductNameSnapshot(), item.getSkuSnapshot(), item.getUnitLabelSnapshot(), item.getOrderedQuantity(), item.getQuantity(), item.getBatchNumber(), item.getExpiryDate())).toList()))
                .toList();
    }

    public PurchaseResponse cancel(UUID id, CancelPurchaseRequest req) {
        Purchase purchase = loadPurchase(id);
        if (purchase.getStatus() != PurchaseStatus.PENDING_GRN) {
            throw new BusinessException("INVALID_STATUS", "Only confirmed purchase orders awaiting GRN can be cancelled");
        }

        String reason = req.reason().trim();
        String cancellationNote = "Cancelled before GRN: " + reason;
        purchase.setNotes(purchase.getNotes() == null || purchase.getNotes().isBlank()
                ? cancellationNote
                : purchase.getNotes() + "\n" + cancellationNote);
        purchase.setStatus(PurchaseStatus.CANCELLED);
        purchase.setUpdatedBy(requirePrincipal().getId());
        Purchase saved = purchaseRepository.save(purchase);
        auditService.log("PURCHASE_ORDER_CANCELLED", "purchase", saved.getId(), Map.of(
                "purchaseNo", saved.getPurchaseNo(),
                "reason", reason));
        return toResponse(saved, loadSupplier(saved.getSupplierId()));
    }

    public PurchaseResponse markPaid(UUID id) {
        Purchase purchase = loadPurchase(id);
        BigDecimal balance = balanceDue(purchase);
        if (balance.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("ALREADY_PAID", "This purchase is already fully paid");
        return recordPayment(id, new RecordPurchasePaymentRequest(balance, PaymentMethod.CASH, "Marked paid"));
    }

    public PurchaseResponse recordPayment(UUID id, RecordPurchasePaymentRequest req) {
        UUID tenantId = TenantContext.require();
        Purchase purchase = purchaseRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase", id));

        if (purchase.getStatus() != PurchaseStatus.BILLED) {
            throw new BusinessException("INVALID_STATUS", "Receive the purchase before recording a supplier payment");
        }
        if (req.method() == PaymentMethod.CREDIT || req.method() == PaymentMethod.SPLIT_COMPONENT) throw new BusinessException("INVALID_PAYMENT_METHOD", "Use Cash, UPI, or Card for supplier payments");
        BigDecimal amount = MoneyUtils.roundHalfUp2(req.amount());
        BigDecimal balance = balanceDue(purchase);
        if (amount.compareTo(balance) > 0) throw new BusinessException("PAYMENT_EXCEEDS_BALANCE", "Payment cannot be more than the remaining balance of " + balance);

        PurchasePayment payment = new PurchasePayment();
        payment.setTenantId(tenantId); payment.setPurchase(purchase); payment.setMethod(req.method()); payment.setAmount(amount);
        payment.setNotes(blankToNull(req.notes())); payment.setCreatedBy(requirePrincipal().getId()); payment.setUpdatedBy(requirePrincipal().getId());
        purchase.getPayments().add(payment);

        if (balance.subtract(amount).compareTo(BigDecimal.ZERO) == 0) purchase.setStatus(PurchaseStatus.PAID);
        purchase.setUpdatedBy(requirePrincipal().getId());
        return toResponse(purchaseRepository.save(purchase), loadSupplier(purchase.getSupplierId()));
    }

    @Transactional(readOnly = true)
    public SupplierOutstandingResponse supplierOutstanding(UUID supplierId) {
        UUID tenantId = TenantContext.require();
        Supplier supplier = supplierRepository.findById(supplierId).orElseThrow(() -> new EntityNotFoundException("Supplier", supplierId));
        List<SupplierOutstandingResponse.Bill> bills = purchaseRepository.findByTenantIdAndSupplierIdAndStatusOrderByBillDateAsc(tenantId, supplierId, PurchaseStatus.BILLED).stream()
                .map(p -> new SupplierOutstandingResponse.Bill(p.getId(), p.getPurchaseNo(), p.getGrandTotal(), totalPaid(p), balanceDue(p), p.getBillDate().toString()))
                .filter(b -> b.balanceDue().compareTo(BigDecimal.ZERO) > 0).toList();
        BigDecimal total = bills.stream().map(SupplierOutstandingResponse.Bill::balanceDue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SupplierOutstandingResponse(supplierId, supplier.getName(), total, bills);
    }

    @Transactional(readOnly = true)
    public SupplierPaymentHistoryResponse supplierPaymentHistory(UUID supplierId) {
        UUID tenantId = TenantContext.require();
        Supplier supplier = supplierRepository.findById(supplierId).orElseThrow(() -> new EntityNotFoundException("Supplier", supplierId));
        Map<String, List<PurchasePayment>> groups = new java.util.LinkedHashMap<>();
        for (PurchasePayment payment : purchasePaymentRepository.findByTenantIdAndPurchase_SupplierIdOrderByCreatedAtDesc(tenantId, supplierId)) {
            String key = payment.getSettlementRef() != null ? payment.getSettlementRef() : "PAY-" + payment.getId();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(payment);
        }
        List<SupplierPaymentHistoryResponse.Settlement> settlements = groups.entrySet().stream().map(entry -> {
            PurchasePayment first = entry.getValue().getFirst();
            BigDecimal total = entry.getValue().stream().map(PurchasePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<SupplierPaymentHistoryResponse.Allocation> allocations = entry.getValue().stream()
                    .map(p -> new SupplierPaymentHistoryResponse.Allocation(p.getPurchase().getId(), p.getPurchase().getPurchaseNo(), p.getAmount())).toList();
            return new SupplierPaymentHistoryResponse.Settlement(entry.getKey(), first.getMethod(), total, first.getNotes(), first.getCreatedAt(), allocations);
        }).toList();
        return new SupplierPaymentHistoryResponse(supplierId, supplier.getName(), settlements);
    }

    public SupplierOutstandingResponse recordSupplierSettlement(UUID supplierId, RecordSupplierSettlementRequest req) {
        UUID tenantId = TenantContext.require();
        if (req.method() == PaymentMethod.CREDIT || req.method() == PaymentMethod.SPLIT_COMPONENT) throw new BusinessException("INVALID_PAYMENT_METHOD", "Use Cash, UPI, or Card for supplier payments");
        BigDecimal amount = MoneyUtils.roundHalfUp2(req.amount());
        Map<UUID, BigDecimal> allocations = new HashMap<>();
        for (RecordSupplierSettlementRequest.Allocation a : req.allocations()) allocations.merge(a.purchaseId(), MoneyUtils.roundHalfUp2(a.amount()), BigDecimal::add);
        BigDecimal allocated = allocations.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(amount) != 0) throw new BusinessException("ALLOCATION_MISMATCH", "Allocated amount must equal the supplier payment amount");
        String settlementRef = "SP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UserPrincipal user = requirePrincipal();
        for (Map.Entry<UUID, BigDecimal> allocation : allocations.entrySet()) {
            Purchase purchase = purchaseRepository.findByIdAndTenantId(allocation.getKey(), tenantId).orElseThrow(() -> new EntityNotFoundException("Purchase", allocation.getKey()));
            if (!purchase.getSupplierId().equals(supplierId) || purchase.getStatus() != PurchaseStatus.BILLED) throw new BusinessException("INVALID_ALLOCATION", "Select billed purchases from this supplier only");
            if (allocation.getValue().compareTo(balanceDue(purchase)) > 0) throw new BusinessException("PAYMENT_EXCEEDS_BALANCE", "Allocation exceeds the remaining balance for " + purchase.getPurchaseNo());
            PurchasePayment payment = new PurchasePayment();
            payment.setTenantId(tenantId); payment.setPurchase(purchase); payment.setMethod(req.method()); payment.setAmount(allocation.getValue());
            payment.setNotes(blankToNull(req.notes())); payment.setSettlementRef(settlementRef); payment.setCreatedBy(user.getId()); payment.setUpdatedBy(user.getId());
            purchase.getPayments().add(payment);
            if (balanceDue(purchase).subtract(allocation.getValue()).compareTo(BigDecimal.ZERO) == 0) purchase.setStatus(PurchaseStatus.PAID);
            purchase.setUpdatedBy(user.getId()); purchaseRepository.save(purchase);
        }
        return supplierOutstanding(supplierId);
    }

    public void deleteDraft(UUID id) {
        UUID tenantId = TenantContext.require();
        Purchase purchase = purchaseRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase", id));

        if (purchase.getStatus() != PurchaseStatus.DRAFT) {
            throw new BusinessException("INVALID_STATUS", "Only draft purchases can be deleted");
        }

        int deleted = purchaseRepository.deleteDraftByIdAndTenantId(id, tenantId);
        if (deleted == 0) {
            throw new EntityNotFoundException("Purchase", id);
        }
    }

    private Purchase loadDraftOrPending(UUID id) {
        UUID tenantId = TenantContext.require();
        return purchaseRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase", id));
    }

    private Supplier loadSupplier(UUID supplierId) {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new EntityNotFoundException("Supplier", supplierId));
    }

    private List<ComputedLine> computeLines(List<PurchaseLineRequest> items) {
        List<ComputedLine> result = new ArrayList<>();
        for (PurchaseLineRequest line : items) {
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> new EntityNotFoundException("Product", line.productId()));
            if (!product.isActive()) {
                throw new BusinessException("PRODUCT_INACTIVE", "Product is inactive: " + product.getName());
            }

            BigDecimal qty = MoneyUtils.roundHalfUp3(line.quantity());
            BigDecimal rate = MoneyUtils.roundHalfUp2(line.rate());
            BigDecimal gstRate = line.gstRatePct() != null
                    ? line.gstRatePct()
                    : product.getTaxRatePct();
            BigDecimal amount = MoneyUtils.roundHalfUp2(rate.multiply(qty));
            BigDecimal gstAmount = MoneyUtils.pct(amount, gstRate);

            result.add(new ComputedLine(product, qty, rate, gstRate, amount, gstAmount));
        }
        return result;
    }

    private Totals sumTotals(List<ComputedLine> lines) {
        BigDecimal subtotal = lines.stream()
                .map(ComputedLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gstTotal = lines.stream()
                .map(ComputedLine::gstAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Totals(
                MoneyUtils.roundHalfUp2(subtotal),
                MoneyUtils.roundHalfUp2(gstTotal),
                MoneyUtils.roundHalfUp2(subtotal.add(gstTotal)));
    }

    private PurchaseItem toItem(Purchase purchase, ComputedLine cl) {
        PurchaseItem item = new PurchaseItem();
        item.setPurchase(purchase);
        item.setProduct(cl.product());
        item.setQuantity(cl.quantity());
        item.setRate(cl.rate());
        item.setGstRate(cl.gstRate());
        item.setAmount(cl.amount());
        item.setGstAmount(cl.gstAmount());
        return item;
    }

    private String nextPurchaseNo(UUID tenantId) {
        String fy = String.valueOf(Instant.now().atZone(IST).getYear());
        PurchaseSequenceId seqId = new PurchaseSequenceId(tenantId, fy);
        PurchaseSequence seq = sequenceRepository.findForUpdate(tenantId, fy)
                .orElseGet(() -> {
                    PurchaseSequence created = new PurchaseSequence();
                    created.setId(seqId);
                    created.setLastNo(0);
                    return created;
                });
        seq.setLastNo(seq.getLastNo() + 1);
        sequenceRepository.save(seq);
        return "PB-" + fy + "-" + String.format("%05d", seq.getLastNo());
    }

    private PurchaseResponse toResponse(Purchase purchase, Supplier supplier) {
        List<PurchaseLineResponse> lines = purchase.getItems().stream()
                .map(i -> new PurchaseLineResponse(
                        i.getId(),
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getProduct().getSku(),
                        i.getProduct().getUnitLabel(),
                        i.getQuantity(),
                        i.getRate(),
                        i.getGstRate(),
                        i.getAmount(),
                        i.getGstAmount(),
                        MoneyUtils.roundHalfUp2(i.getAmount().add(i.getGstAmount())),
                        receivedQuantity(i), i.getProduct().isExpiryTracked()))
                .toList();
        List<PurchasePaymentResponse> payments = purchase.getPayments().stream()
                .map(p -> new PurchasePaymentResponse(p.getId(), p.getMethod(), p.getAmount(), p.getNotes(), p.getCreatedAt())).toList();
        BigDecimal totalPaid = payments.stream().map(PurchasePaymentResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balanceDue = purchase.getGrandTotal().subtract(totalPaid).max(BigDecimal.ZERO);

        return new PurchaseResponse(
                purchase.getId(),
                purchase.getPurchaseNo(),
                supplier.getId(),
                supplier.getName(),
                supplier.getGstin(),
                supplier.getPhone(),
                purchase.getBillDate(),
                purchase.getDueDate(),
                purchase.getStatus(),
                purchase.getSubtotal(),
                purchase.getGstTotal(),
                purchase.getGrandTotal(),
                totalPaid,
                balanceDue,
                purchase.getNotes(),
                purchase.getCreatedAt(),
                purchase.getUpdatedAt(),
                payments,
                lines);
    }

    private Purchase loadPurchase(UUID id) {
        UUID tenantId = TenantContext.require();
        return purchaseRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new EntityNotFoundException("Purchase", id));
    }

    private static BigDecimal balanceDue(Purchase purchase) {
        return purchase.getGrandTotal().subtract(totalPaid(purchase)).max(BigDecimal.ZERO);
    }

    private static BigDecimal totalPaid(Purchase purchase) {
        return purchase.getPayments().stream().map(PurchasePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal receivedQuantity(PurchaseItem item) {
        return purchaseReceiptItemRepository.receivedQuantity(item.getId());
    }

    private static List<PurchaseStatus> parseStatuses(String raw) {
        if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw)) {
            return List.of(PurchaseStatus.values());
        }
        String normalized = raw.trim().toLowerCase();
        if ("orders".equals(normalized) || "purchase_orders".equals(normalized)) {
            return List.of(PurchaseStatus.values());
        }
        if ("receipts".equals(normalized) || "purchase_receipts".equals(normalized)) {
            return List.of(PurchaseStatus.PENDING_GRN, PurchaseStatus.BILLED, PurchaseStatus.PAID);
        }
        try {
            return List.of(PurchaseStatus.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return switch (normalized) {
                case "draft" -> List.of(PurchaseStatus.DRAFT);
                case "pending", "pending_grn" -> List.of(PurchaseStatus.PENDING_GRN);
                case "cancelled", "canceled" -> List.of(PurchaseStatus.CANCELLED);
                case "billed" -> List.of(PurchaseStatus.BILLED);
                case "paid" -> List.of(PurchaseStatus.PAID);
                case "completed" -> List.of(PurchaseStatus.BILLED, PurchaseStatus.PAID);
                default -> throw new BusinessException("INVALID_STATUS", "Unknown status filter: " + raw);
            };
        }
    }

    private static String blankToNull(String q) {
        return q == null || q.isBlank() ? null : q.trim();
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private UserPrincipal requirePrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal up) {
            return up;
        }
        throw new BusinessException("UNAUTHENTICATED", "User context required");
    }

    private record ComputedLine(
            Product product,
            BigDecimal quantity,
            BigDecimal rate,
            BigDecimal gstRate,
            BigDecimal amount,
            BigDecimal gstAmount) {}

    private record Totals(BigDecimal subtotal, BigDecimal gstTotal, BigDecimal grandTotal) {}
}
