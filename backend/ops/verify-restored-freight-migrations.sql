-- Run ONLY on a freshly restored, isolated LOCAL database. No application startup.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
SET LOCAL search_path = public;
DO $$ BEGIN
 IF current_database() NOT LIKE 'ingarage_restore_%' OR host(inet_server_addr()) <> '127.0.0.1'
 THEN RAISE EXCEPTION 'Only a new isolated local restore is allowed'; END IF;
 IF to_regclass('public.orders') IS NULL THEN RAISE EXCEPTION 'Restore the existing schema first'; END IF;
END $$;
CREATE TEMP TABLE migration_baseline (table_name TEXT PRIMARY KEY, fingerprint TEXT);
DO $$ DECLARE t RECORD; checksum TEXT; BEGIN
 FOR t IN SELECT tablename FROM pg_tables WHERE schemaname='public' LOOP
  EXECUTE format('SELECT md5(coalesce(string_agg(row_hash, '''' ORDER BY row_hash), '''')) FROM (SELECT md5((to_jsonb(r)-''assisted_quote''-''request_key''-''commercial_shipping'')::text) row_hash FROM public.%I r) hashes',t.tablename) INTO checksum;
  INSERT INTO migration_baseline VALUES(t.tablename,checksum);
 END LOOP;
END $$;
-- Spring SQL init scripts; this is not a Flyway execution.
\ir ../src/main/resources/db/migration/V20261030_01__assisted_quote.sql
\ir ../src/main/resources/db/migration/V20261030_02__commercial_freight.sql
-- Idempotence: repeat in the same order.
\ir ../src/main/resources/db/migration/V20261030_01__assisted_quote.sql
\ir ../src/main/resources/db/migration/V20261030_02__commercial_freight.sql
DO $$ DECLARE t RECORD; checksum TEXT; BEGIN
 FOR t IN SELECT * FROM migration_baseline LOOP
  EXECUTE format('SELECT md5(coalesce(string_agg(row_hash, '''' ORDER BY row_hash), '''')) FROM (SELECT md5((to_jsonb(r)-''assisted_quote''-''request_key''-''commercial_shipping'')::text) row_hash FROM public.%I r) hashes',t.table_name) INTO checksum;
  IF checksum IS DISTINCT FROM t.fingerprint THEN RAISE EXCEPTION 'Existing records changed'; END IF;
 END LOOP;
 IF EXISTS(SELECT 1 FROM orders WHERE assisted_quote IS NOT NULL OR commercial_shipping IS NOT NULL OR request_key IS NOT NULL)
 THEN RAISE EXCEPTION 'Expected legacy rows without new snapshots'; END IF;
 IF EXISTS(SELECT 1 FROM commercial_freight_tables) THEN RAISE EXCEPTION 'No real prices may be seeded'; END IF;
END $$;
SELECT 'MIGRATIONS_VERIFIED_NO_EXISTING_RECORDS_CHANGED' AS result,
 (SELECT count(*) FROM migration_baseline) AS existing_tables_verified;
COMMIT;
