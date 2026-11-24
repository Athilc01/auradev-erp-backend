package com.auradev.erp.settings.model;

/**
 * Receipt printer preferences stored in {@code tenant_settings.printer} JSONB.
 */
public record PrinterConfig(
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
) {
    public static PrinterConfig defaults() {
        return new PrinterConfig(80, false, false, 1, true, true, true, true, true, true, true, true, true, true, true, "SALES RECEIPT", false, "RECEIPT_PRINTER", "", "DRAWER_1", true, false);
    }

    public PrinterConfig normalized() {
        int w = widthMm == 58 ? 58 : 80;
        int c = copies < 1 ? 1 : Math.min(copies, 3);
        String title = receiptTitle == null || receiptTitle.isBlank() ? "SALES RECEIPT" : receiptTitle.trim();
        if (title.length() > 48) title = title.substring(0, 48);
        String connection = "USB".equals(cashDrawerConnection) || "NETWORK".equals(cashDrawerConnection) ? cashDrawerConnection : "RECEIPT_PRINTER";
        String name = cashDrawerName == null ? "" : cashDrawerName.trim();
        if (name.length() > 80) name = name.substring(0, 80);
        String port = "DRAWER_2".equals(cashDrawerPort) ? "DRAWER_2" : "DRAWER_1";
        return new PrinterConfig(w, autoPrint, autoDownloadReceipt, c, showLogo, showStorePhone, showStoreAddress, showStoreTin,
                showBillDate, showCustomer, showCashier, showPayment, showTendered, showChange, showTaxBreakup, title,
                cashDrawerEnabled, connection, name, port, openDrawerOnCashPayment, allowManualDrawerOpen);
    }
}
