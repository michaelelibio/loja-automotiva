-- Read-only metadata inspection. No order/customer rows, DDL, DML or migration execution.
\set ON_ERROR_STOP on
BEGIN READ ONLY;
SET LOCAL statement_timeout = '15s';
SELECT current_database() AS database_name, current_setting('server_version') AS server_version,
       current_setting('transaction_read_only') AS read_only;
WITH expected(table_name,column_name,data_type,is_nullable) AS (VALUES
 ('commercial_freight_tables','id','bigint','NO'),
 ('commercial_freight_tables','document','jsonb','NO'),
 ('commercial_freight_tables','created_at','timestamp with time zone','NO'),
 ('commercial_freight_tables','published_at','timestamp with time zone','YES'),
 ('commercial_freight_tables','enabled','boolean','NO'),
 ('orders','commercial_shipping','jsonb','YES'),
 ('payments','provider_live_mode','boolean','YES'),
 ('payments','provider_verified_at','timestamp with time zone','YES'),
 ('order_fulfillments','supplier_sync','jsonb','YES')
)
SELECT e.*, c.data_type AS actual_type, c.is_nullable AS actual_nullable,
       coalesce(c.data_type=e.data_type AND c.is_nullable=e.is_nullable,false) AS compatible
FROM expected e LEFT JOIN information_schema.columns c
 ON c.table_schema='public' AND c.table_name=e.table_name AND c.column_name=e.column_name
ORDER BY e.table_name,e.column_name;
SELECT CASE WHEN to_regclass('public.admin_aliexpress_test_runs') IS NULL
 THEN 'PENDING_V20261104_01' ELSE 'PRESENT_CHECK_COLUMNS_AND_CONSTRAINTS' END AS admin_test_schema;
SELECT column_name,data_type,is_nullable,character_maximum_length
FROM information_schema.columns
WHERE table_schema='public' AND table_name='admin_aliexpress_test_runs'
ORDER BY ordinal_position;
SELECT conname,pg_get_constraintdef(oid) AS definition FROM pg_constraint
WHERE conrelid=to_regclass('public.admin_aliexpress_test_runs') ORDER BY conname;
COMMIT;
