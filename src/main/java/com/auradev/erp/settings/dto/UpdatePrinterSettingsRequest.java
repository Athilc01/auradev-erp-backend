package com.auradev.erp.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdatePrinterSettingsRequest(
        @Min(58) @Max(80) Integer widthMm,
        Boolean autoPrint,
        Boolean autoDownloadReceipt,
        @Min(1) @Max(3) Integer copies,
        Boolean showLogo,
        Boolean showStorePhone,
        Boolean showStoreAddress,
        Boolean showStoreTin,
        Boolean showBillDate,
        Boolean showCustomer,
        Boolean showCashier,
        Boolean showPayment,
        Boolean showTendered,
        Boolean showChange,
        Boolean showTaxBreakup,
        @jakarta.validation.constraints.Size(max = 48) String receiptTitle,
        Boolean cashDrawerEnabled,
        @jakarta.validation.constraints.Pattern(regexp = "RECEIPT_PRINTER|USB|NETWORK") String cashDrawerConnection,
        @jakarta.validation.constraints.Size(max = 80) String cashDrawerName,
        @jakarta.validation.constraints.Pattern(regexp = "DRAWER_1|DRAWER_2") String cashDrawerPort,
        Boolean openDrawerOnCashPayment,
        Boolean allowManualDrawerOpen
) {}
