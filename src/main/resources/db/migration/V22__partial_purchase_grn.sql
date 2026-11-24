CREATE TABLE purchase_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    purchase_id UUID NOT NULL REFERENCES purchases(id),
    grn_no VARCHAR(40) NOT NULL,
    notes TEXT,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, grn_no)
);
CREATE INDEX idx_purchase_receipts_purchase ON purchase_receipts(tenant_id, purchase_id, created_at DESC);

CREATE TABLE purchase_receipt_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id UUID NOT NULL REFERENCES purchase_receipts(id) ON DELETE CASCADE,
    purchase_item_id UUID NOT NULL REFERENCES purchase_items(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity NUMERIC(12,3) NOT NULL CHECK (quantity > 0)
);
CREATE INDEX idx_purchase_receipt_items_purchase_item ON purchase_receipt_items(purchase_item_id);
