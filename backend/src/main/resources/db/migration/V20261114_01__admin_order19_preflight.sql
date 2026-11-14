-- Additive, operator-applied in controlled. Never rewrite attempts or backfill an unproven buyer.
ALTER TABLE admin_aliexpress_test_runs ADD COLUMN IF NOT EXISTS preflight JSONB;
ALTER TABLE aliexpress_authorizations ADD COLUMN IF NOT EXISTS buyer_user_id VARCHAR(255);
ALTER TABLE aliexpress_authorizations ADD COLUMN IF NOT EXISTS buyer_account_id VARCHAR(255);
