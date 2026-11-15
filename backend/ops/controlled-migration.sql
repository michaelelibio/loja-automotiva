-- Execute manually with psql -X -v ON_ERROR_STOP=1 -f backend/ops/controlled-migration.sql.
-- Backup and isolated restore verification are mandatory operational prerequisites.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
SET LOCAL search_path = public;
DO $$
BEGIN
    IF to_regclass('public.orders') IS NULL OR to_regclass('public.order_fulfillments') IS NULL THEN
        RAISE EXCEPTION 'Existing order schema is required; do not bootstrap production here';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema='public' AND table_name='orders' AND column_name='recipient_cpf')
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema='public' AND table_name='orders' AND column_name='shipping_legs') THEN
        RAISE EXCEPTION 'Prior recipient/shipping migrations missing: review baseline before proceeding';
    END IF;
END $$;
\ir ../src/main/resources/db/migration/V20261030_01__assisted_quote.sql
COMMIT;
