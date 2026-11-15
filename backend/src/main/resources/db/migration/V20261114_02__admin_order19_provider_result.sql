-- Additive result storage. Never reset a supplier attempt.
ALTER TABLE admin_aliexpress_test_runs ADD COLUMN IF NOT EXISTS provider_error JSONB;
