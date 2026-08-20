ALTER TABLE order_items ADD COLUMN IF NOT EXISTS fulfillment_type VARCHAR(30);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS supplier VARCHAR(50);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS supplier_product_id VARCHAR(150);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS supplier_variant_id VARCHAR(150);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS supplier_sku VARCHAR(150);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS product_variant_id BIGINT;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS variant_name VARCHAR(500);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS supplier_cost NUMERIC(19, 4);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS supplier_cost_currency VARCHAR(3);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS weight_grams NUMERIC(19, 4);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS length_mm NUMERIC(19, 4);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS width_mm NUMERIC(19, 4);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS height_mm NUMERIC(19, 4);

CREATE INDEX IF NOT EXISTS ix_order_items_product_variant_id
    ON order_items (product_variant_id);
