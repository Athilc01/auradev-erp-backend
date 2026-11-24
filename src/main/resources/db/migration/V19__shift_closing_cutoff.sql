-- Existing tenants receive the safe default: unfinished shifts become overdue
-- at the end of the configured business day instead of being silently closed.
UPDATE tenant_settings
SET billing = billing || '{"markOverdueShiftsAtClose": true, "shiftClosingTime": "23:59"}'::jsonb
WHERE NOT (billing ? 'markOverdueShiftsAtClose');
