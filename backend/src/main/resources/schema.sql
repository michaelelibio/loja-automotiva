ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;

UPDATE orders
SET expires_at = COALESCE(created_at, CURRENT_TIMESTAMP) + INTERVAL '24 hours'
WHERE expires_at IS NULL;

ALTER TABLE orders
    ALTER COLUMN expires_at SET NOT NULL;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS provider_order_id VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_provider_order_id
    ON payments (provider_order_id);
