-- Additive, no backfill: historical orders and other suppliers retain their states.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS recipient_cpf VARCHAR(11);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS recipient_birth_date DATE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(15);
ALTER TABLE order_fulfillments ADD COLUMN IF NOT EXISTS supplier_order_ids JSONB;
ALTER TABLE order_fulfillments ADD COLUMN IF NOT EXISTS supplier_status VARCHAR(40);
ALTER TABLE order_fulfillments DROP CONSTRAINT IF EXISTS ck_order_fulfillments_status;
ALTER TABLE order_fulfillments DROP CONSTRAINT IF EXISTS order_fulfillments_status_check;
ALTER TABLE order_fulfillments ADD CONSTRAINT ck_order_fulfillments_status CHECK
    (status IN ('NOT_REQUIRED', 'PENDING', 'PROCESSING', 'CREATED', 'FAILED', 'VERIFICATION_REQUIRED'));
