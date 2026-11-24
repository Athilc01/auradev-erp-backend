-- Customer identity on a completed bill must remain historically accurate even
-- when the customer record is later corrected or their delivery address changes.
ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS customer_name_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS customer_address_snapshot TEXT;

UPDATE bills b
SET customer_name_snapshot = c.name,
    customer_address_snapshot = c.address
FROM customers c
WHERE c.id = b.customer_id
  AND b.customer_name_snapshot IS NULL;

ALTER TABLE bills
    ALTER COLUMN customer_name_snapshot SET NOT NULL;

COMMENT ON COLUMN bills.customer_name_snapshot IS 'Customer name at the time the bill was saved';
COMMENT ON COLUMN bills.customer_address_snapshot IS 'Customer delivery address at the time the bill was saved';
