ALTER TYPE movement_type ADD VALUE IF NOT EXISTS 'purchase_return';

CREATE TABLE purchase_returns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    purchase_id UUID NOT NULL REFERENCES purchases(id),
    return_no VARCHAR(40) NOT NULL,
    reason TEXT NOT NULL,
    resolution VARCHAR(30) NOT NULL,
    return_total NUMERIC(12,2) NOT NULL CHECK (return_total >= 0),
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, return_no)
);
CREATE INDEX idx_purchase_returns_purchase ON purchase_returns(tenant_id, purchase_id, created_at DESC);

CREATE TABLE purchase_return_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_id UUID NOT NULL REFERENCES purchase_returns(id) ON DELETE CASCADE,
    purchase_item_id UUID NOT NULL REFERENCES purchase_items(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity NUMERIC(12,3) NOT NULL CHECK (quantity > 0),
    return_amount NUMERIC(12,2) NOT NULL CHECK (return_amount >= 0)
);
CREATE INDEX idx_purchase_return_items_purchase_item ON purchase_return_items(purchase_item_id);
