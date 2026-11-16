-- Run ONLY in an empty disposable PostgreSQL database, never production.
\set ON_ERROR_STOP on
BEGIN;
CREATE SCHEMA catalog_migration_test;
SET LOCAL search_path TO catalog_migration_test;
CREATE TABLE products(id BIGINT PRIMARY KEY, slug TEXT, category TEXT, supplier TEXT, supplier_product_id TEXT, stock_quantity INTEGER, active BOOLEAN);
CREATE TABLE orders(id BIGINT PRIMARY KEY, supplier_reference TEXT);
CREATE TABLE payments(id BIGINT PRIMARY KEY, provider_payment_id TEXT);
INSERT INTO products VALUES (1,'produto-existente','Categoria legada','ALIEXPRESS','supplier-original',5,true),(2,'produto-cj','Legada CJ','CJ','cj-original',0,false);
INSERT INTO orders VALUES (21,'referencia-preservada');
INSERT INTO payments VALUES (1,'pagamento-preservado');
CREATE TEMP TABLE before_products AS SELECT * FROM products;
\ir ../../main/resources/db/migration/V20261116_01__detailing_catalog.sql
UPDATE catalog_categories SET name='Nome administrado',active=false,position=999 WHERE path='lavagem';
\ir ../../main/resources/db/migration/V20261116_01__detailing_catalog.sql
DO $$
BEGIN
  IF (SELECT count(*) FROM catalog_categories)<>48 OR (SELECT count(*) FROM catalog_categories WHERE parent_id IS NULL)<>8 THEN RAISE EXCEPTION 'Taxonomia incompleta ou duplicada'; END IF;
  IF NOT EXISTS(SELECT 1 FROM catalog_categories WHERE path='lavagem' AND name='Nome administrado' AND active=false AND position=999) THEN RAISE EXCEPTION 'Seed sobrescreveu edição'; END IF;
  IF EXISTS((SELECT id,slug,category,supplier,supplier_product_id,stock_quantity,active FROM products EXCEPT SELECT * FROM before_products) UNION ALL (SELECT * FROM before_products EXCEPT SELECT id,slug,category,supplier,supplier_product_id,stock_quantity,active FROM products)) THEN RAISE EXCEPTION 'Dados existentes alterados'; END IF;
  IF EXISTS(SELECT 1 FROM products WHERE catalog_category_id IS NOT NULL OR commercial_origin IS NOT NULL OR published_at IS NOT NULL OR featured OR beginner) THEN RAISE EXCEPTION 'Classificação ou publicação histórica inventada'; END IF;
  IF NOT EXISTS(SELECT 1 FROM orders WHERE id=21 AND supplier_reference='referencia-preservada') OR NOT EXISTS(SELECT 1 FROM payments WHERE provider_payment_id='pagamento-preservado') THEN RAISE EXCEPTION 'Pedido ou pagamento alterado'; END IF;
  IF (SELECT count(*) FROM information_schema.table_constraints WHERE table_schema='catalog_migration_test' AND constraint_type='FOREIGN KEY')<>6 THEN RAISE EXCEPTION 'Relacionamentos ausentes'; END IF;
END $$;
SELECT 'PASS: 48 categorias, reexecução idempotente, edições e dados preservados' AS result;
ROLLBACK;
