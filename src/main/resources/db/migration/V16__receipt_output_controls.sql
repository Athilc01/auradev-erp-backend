-- Additional receipt display options are stored in the existing JSONB printer configuration.
-- Existing tenants keep the original behaviour through the application defaults.
UPDATE tenant_settings
SET printer = COALESCE(printer, '{}'::jsonb) || jsonb_build_object(
    'showStorePhone', COALESCE((printer ->> 'showStorePhone')::boolean, true),
    'showStoreAddress', COALESCE((printer ->> 'showStoreAddress')::boolean, true),
    'showStoreTin', COALESCE((printer ->> 'showStoreTin')::boolean, true),
    'showBillDate', COALESCE((printer ->> 'showBillDate')::boolean, true),
    'showCustomer', COALESCE((printer ->> 'showCustomer')::boolean, true),
    'showCashier', COALESCE((printer ->> 'showCashier')::boolean, true),
    'showPayment', COALESCE((printer ->> 'showPayment')::boolean, true),
    'showTendered', COALESCE((printer ->> 'showTendered')::boolean, true),
    'showChange', COALESCE((printer ->> 'showChange')::boolean, true),
    'showTaxBreakup', COALESCE((printer ->> 'showTaxBreakup')::boolean, true),
    'receiptTitle', COALESCE(NULLIF(printer ->> 'receiptTitle', ''), 'SALES RECEIPT')
);
