ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_provider VARCHAR(30);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_provider_amount NUMERIC(19, 4);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_provider_currency VARCHAR(3);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_legs JSONB;
