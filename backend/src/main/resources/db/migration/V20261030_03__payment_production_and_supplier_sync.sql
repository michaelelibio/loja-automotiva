-- Additive only. NULL means unknown, never evidence of a production payment.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider_live_mode BOOLEAN;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider_verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE order_fulfillments ADD COLUMN IF NOT EXISTS supplier_sync JSONB;
