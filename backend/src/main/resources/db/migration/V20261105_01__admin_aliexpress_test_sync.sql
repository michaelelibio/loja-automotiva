-- Manual additive migration, after V20261104_01. No changes to financial/customer tables.
ALTER TABLE admin_aliexpress_test_runs ADD COLUMN IF NOT EXISTS supplier_sync JSONB;
