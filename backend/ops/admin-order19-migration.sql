-- OPERATOR ONLY after backup and review. This script does not enable order creation.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
SET LOCAL search_path = public;
DO $$ BEGIN
 IF to_regclass('public.orders') IS NULL OR to_regclass('public.aliexpress_authorizations') IS NULL THEN
  RAISE EXCEPTION 'Required order/OAuth base schema missing. Stop and inspect.';
 END IF;
END $$;
\ir ../src/main/resources/db/migration/V20261104_01__admin_aliexpress_test_runs.sql
\ir ../src/main/resources/db/migration/V20261105_01__admin_aliexpress_test_sync.sql
\ir ../src/main/resources/db/migration/V20261114_01__admin_order19_preflight.sql
\ir ../src/main/resources/db/migration/V20261114_02__admin_order19_provider_result.sql
COMMIT;
