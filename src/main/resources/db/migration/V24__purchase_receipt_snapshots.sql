ALTER TABLE purchase_receipts
    ADD COLUMN IF NOT EXISTS purchase_no_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS supplier_name_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS receiver_name_snapshot TEXT;

ALTER TABLE purchase_receipt_items
    ADD COLUMN IF NOT EXISTS product_name_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS sku_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS unit_label_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS ordered_quantity NUMERIC(12,3),
    ADD COLUMN IF NOT EXISTS batch_number TEXT,
    ADD COLUMN IF NOT EXISTS expiry_date DATE;

UPDATE purchase_receipts r SET purchase_no_snapshot = p.purchase_no
FROM purchases p WHERE p.id = r.purchase_id AND r.purchase_no_snapshot IS NULL;
UPDATE purchase_receipts r SET supplier_name_snapshot = s.name
FROM purchases p JOIN suppliers s ON s.id = p.supplier_id
WHERE p.id = r.purchase_id AND r.supplier_name_snapshot IS NULL;
UPDATE purchase_receipt_items ri SET product_name_snapshot = p.name, sku_snapshot = p.sku, unit_label_snapshot = p.unit_label, ordered_quantity = pi.quantity
FROM products p, purchase_items pi
WHERE p.id = ri.product_id
  AND pi.id = ri.purchase_item_id
  AND ri.product_name_snapshot IS NULL;
