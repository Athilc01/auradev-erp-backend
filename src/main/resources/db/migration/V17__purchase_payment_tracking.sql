CREATE TABLE purchase_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
    purchase_id UUID NOT NULL REFERENCES purchases(id) ON DELETE CASCADE, method payment_method NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0), notes TEXT, version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES users(id), updated_by UUID REFERENCES users(id)
);
CREATE INDEX idx_purchase_payments_purchase ON purchase_payments(purchase_id, created_at);
