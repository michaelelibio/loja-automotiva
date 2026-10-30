-- Manual only, after backup and isolated restore rehearsal. No price rows are inserted.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
SET LOCAL search_path = public;
DO $$ BEGIN IF to_regclass('public.orders') IS NULL THEN RAISE EXCEPTION 'Existing order schema required'; END IF; END $$;
\ir ../src/main/resources/db/migration/V20261030_02__commercial_freight.sql
COMMIT;
