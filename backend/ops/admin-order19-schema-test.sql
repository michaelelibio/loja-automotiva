-- This executable verification is restricted to a disposable local database.
\set ON_ERROR_STOP on
SELECT current_database() IN ('admin_order19_schema','admin_order19_schema_v2') AND inet_server_addr() = '127.0.0.1'::inet AS safe_local_test \gset
\if :safe_local_test
\else
\echo 'Refusing: expected local disposable admin_order19_schema database.'
\quit 3
\endif
CREATE TABLE IF NOT EXISTS orders (id BIGINT PRIMARY KEY);
CREATE TABLE IF NOT EXISTS aliexpress_authorizations (id VARCHAR(64) PRIMARY KEY, access_ciphertext VARCHAR(4096));
INSERT INTO orders VALUES (19) ON CONFLICT DO NOTHING;
INSERT INTO aliexpress_authorizations VALUES ('synthetic-app-hash','synthetic-encrypted-token') ON CONFLICT DO NOTHING;
\ir admin-order19-migration.sql
INSERT INTO admin_aliexpress_test_runs(order_id,real_key,real_status,supplier_order_ids,preflight)
 VALUES (19,'ingarage-test-19-v1','VERIFICATION_REQUIRED','["123456789012345678901234567890"]'::jsonb,'{"id":"synthetic-review"}'::jsonb) ON CONFLICT DO NOTHING;
-- Applying the additive migration a second time must preserve the attempt and the OAuth row.
\ir admin-order19-migration.sql
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='admin_aliexpress_test_runs' AND column_name='provider_error' AND data_type='jsonb') THEN RAISE EXCEPTION 'Provider result column missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM admin_aliexpress_test_runs WHERE order_id=19 AND real_key='ingarage-test-19-v1'
   AND real_status='VERIFICATION_REQUIRED' AND supplier_order_ids->>0='123456789012345678901234567890'
   AND preflight->>'id'='synthetic-review') THEN RAISE EXCEPTION 'Attempt changed during migration'; END IF;
 IF NOT EXISTS(SELECT 1 FROM aliexpress_authorizations WHERE id='synthetic-app-hash' AND access_ciphertext='synthetic-encrypted-token'
   AND buyer_user_id IS NULL AND buyer_account_id IS NULL) THEN RAISE EXCEPTION 'OAuth row changed or buyer invented'; END IF;
 BEGIN
   INSERT INTO admin_aliexpress_test_runs(order_id,real_key) VALUES (20,'ingarage-test-19-v1');
   RAISE EXCEPTION 'Duplicate external reference accepted';
 EXCEPTION WHEN unique_violation THEN NULL;
 END;
END $$;
SELECT 'PASS: additive migration, preserved attempts/OAuth and unique reference' AS result;
