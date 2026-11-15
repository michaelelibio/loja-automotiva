-- OPERATOR ONLY, after backup + explicit release approval. Never run by application startup.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
SET LOCAL search_path = public;
DO $$ BEGIN
 IF to_regclass('public.orders') IS NULL OR to_regclass('public.payments') IS NULL
    OR to_regclass('public.order_fulfillments') IS NULL
    OR to_regclass('public.commercial_freight_tables') IS NULL
    OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='orders' AND column_name='commercial_shipping' AND data_type='jsonb')
    OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='payments' AND column_name='provider_live_mode' AND data_type='boolean')
    OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='payments' AND column_name='provider_verified_at' AND data_type='timestamp with time zone')
    OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='order_fulfillments' AND column_name='supplier_sync' AND data_type='jsonb')
 THEN RAISE EXCEPTION 'Base schema V02/V03 missing or incompatible. Stop and inspect; do not replay old migrations.'; END IF;
END $$;
\ir ../src/main/resources/db/migration/V20261104_01__admin_aliexpress_test_runs.sql
COMMIT;
