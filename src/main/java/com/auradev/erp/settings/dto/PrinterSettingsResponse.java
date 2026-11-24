package com.auradev.erp.settings.dto;

public record PrinterSettingsResponse(
        int widthMm,
        boolean autoPrint,
        boolean autoDownloadReceipt,
        int copies,
        boolean showLogo,
        boolean showStorePhone,
        boolean showStoreAddress,
        boolean showStoreTin,
        boolean showBillDate,
        boolean showCustomer,
        boolean showCashier,
        boolean showPayment,
        boolean showTendered,
        boolean showChange,
        boolean showTaxBreakup,
        String receiptTitle,
        boolean cashDrawerEnabled,
        String cashDrawerConnection,
        String cashDrawerName,
        String cashDrawerPort,
        boolean openDrawerOnCashPayment,
        boolean allowManualDrawerOpen
) {}
