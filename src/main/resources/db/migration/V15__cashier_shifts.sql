CREATE TABLE cashier_shifts (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  cashier_id uuid NOT NULL,
  status varchar(12) NOT NULL,
  opening_cash numeric(12,2) NOT NULL,
  opened_at timestamptz NOT NULL,
  closed_at timestamptz,
  expected_cash numeric(12,2),
  expected_upi numeric(12,2),
  expected_card numeric(12,2),
  expected_credit numeric(12,2),
  counted_cash numeric(12,2),
  variance numeric(12,2),
  closing_note text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid,
  updated_by uuid,
  version bigint NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_cashier_shifts_open ON cashier_shifts(tenant_id, cashier_id) WHERE status = 'OPEN';
CREATE INDEX ix_cashier_shifts_tenant_opened ON cashier_shifts(tenant_id, opened_at DESC);
