ALTER TABLE purchase_payments ADD COLUMN settlement_ref VARCHAR(32);
CREATE INDEX idx_purchase_payments_settlement_ref ON purchase_payments(tenant_id, settlement_ref) WHERE settlement_ref IS NOT NULL;
