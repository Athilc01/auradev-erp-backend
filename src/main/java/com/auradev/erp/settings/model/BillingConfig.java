package com.auradev.erp.settings.model;

/**
 * POS / billing behaviour stored in {@code tenant_settings.billing} JSONB.
 */
public record BillingConfig(
        int maxLineDiscountPercent,
        int maxBillDiscountPercent,
        int cashierMaxBillDiscountPercent,
        boolean allowHoldBill,
        boolean allowCreditSales,
        boolean taxEnabled,
        boolean showCashierOnReceipt,
        boolean showGstBreakupOnReceipt,
        boolean showCustomerOnReceipt,
        boolean roundTotalToRupee,
        boolean markOverdueShiftsAtClose,
        String shiftClosingTime
) {
    public static BillingConfig defaults() {
        return new BillingConfig(10, 15, 5, true, true, false, true, true, true, false, true, "23:59");
    }

    public BillingConfig normalized() {
        return new BillingConfig(
                clamp(maxLineDiscountPercent, 0, 100),
                clamp(maxBillDiscountPercent, 0, 100),
                clamp(cashierMaxBillDiscountPercent, 0, 100),
                allowHoldBill,
                allowCreditSales,
                taxEnabled,
                showCashierOnReceipt,
                showGstBreakupOnReceipt,
                showCustomerOnReceipt,
                roundTotalToRupee,
                markOverdueShiftsAtClose,
                normalizeCloseTime(shiftClosingTime));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String normalizeCloseTime(String value) {
        if (value != null && value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) return value;
        return "23:59";
    }
}
