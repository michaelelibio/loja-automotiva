ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;

UPDATE orders
SET expires_at = COALESCE(created_at, CURRENT_TIMESTAMP) + INTERVAL '24 hours'
WHERE expires_at IS NULL;

ALTER TABLE orders
    ALTER COLUMN expires_at SET NOT NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER';

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS account_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_account_tokens_user_type
    ON account_tokens (user_id, type);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS processing_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipped_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_code VARCHAR(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_name VARCHAR(120);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_estimated_days INTEGER;

UPDATE orders
SET shipping_code = COALESCE(shipping_code, 'LEGACY'),
    shipping_name = COALESCE(shipping_name, 'Frete legado'),
    shipping_estimated_days = COALESCE(shipping_estimated_days, 0)
WHERE shipping_code IS NULL OR shipping_name IS NULL OR shipping_estimated_days IS NULL;

ALTER TABLE orders ALTER COLUMN shipping_code SET NOT NULL;
ALTER TABLE orders ALTER COLUMN shipping_name SET NOT NULL;
ALTER TABLE orders ALTER COLUMN shipping_estimated_days SET NOT NULL;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS provider_order_id VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_provider_order_id
    ON payments (provider_order_id);

ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider_preference_id VARCHAR(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS external_reference VARCHAR(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS checkout_url VARCHAR(2000);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider_payment_type VARCHAR(100);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider_payment_method_id VARCHAR(100);

ALTER TABLE payments
    DROP CONSTRAINT IF EXISTS payments_method_check;
ALTER TABLE payments
    ADD CONSTRAINT payments_method_check
    CHECK (method IN ('PIX', 'MERCADO_PAGO'));

CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_provider_preference_id
    ON payments (provider_preference_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_external_reference
    ON payments (external_reference);

ALTER TABLE products ADD COLUMN IF NOT EXISTS cost_price NUMERIC(12, 2);
ALTER TABLE products ADD COLUMN IF NOT EXISTS sku VARCHAR(100);
ALTER TABLE products ADD COLUMN IF NOT EXISTS supplier VARCHAR(50);
ALTER TABLE products ADD COLUMN IF NOT EXISTS supplier_product_id VARCHAR(150);
ALTER TABLE products ADD COLUMN IF NOT EXISTS fulfillment_type VARCHAR(30);

UPDATE products
SET fulfillment_type = CASE
    WHEN UPPER(COALESCE(supplier, '')) = 'CJ' THEN 'DROPSHIPPING'
    ELSE 'LOCAL_STOCK'
END
WHERE fulfillment_type IS NULL;

ALTER TABLE products ALTER COLUMN fulfillment_type SET DEFAULT 'LOCAL_STOCK';
ALTER TABLE products ALTER COLUMN fulfillment_type SET NOT NULL;
ALTER TABLE products ADD COLUMN IF NOT EXISTS supplier_cost_usd NUMERIC(19, 4);
ALTER TABLE products ADD COLUMN IF NOT EXISTS supplier_exchange_rate NUMERIC(19, 6);
ALTER TABLE products ADD COLUMN IF NOT EXISTS supplier_cost_updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE products ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE products ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;

UPDATE products
SET created_at = COALESCE(created_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
WHERE created_at IS NULL OR updated_at IS NULL;

ALTER TABLE products ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE products ALTER COLUMN updated_at SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_products_sku ON products (sku);
CREATE UNIQUE INDEX IF NOT EXISTS ux_products_supplier_product_id
    ON products (supplier_product_id);

CREATE TABLE IF NOT EXISTS stock_movements (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    previous_stock INTEGER NOT NULL CHECK (previous_stock >= 0),
    new_stock INTEGER NOT NULL CHECK (new_stock >= 0),
    reason VARCHAR(500) NOT NULL,
    reference_type VARCHAR(40),
    reference_id BIGINT,
    performed_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_stock_movement_reference_product_type
        UNIQUE (product_id, type, reference_type, reference_id)
);

CREATE INDEX IF NOT EXISTS ix_stock_movements_product
    ON stock_movements (product_id);
CREATE INDEX IF NOT EXISTS ix_stock_movements_type
    ON stock_movements (type);
CREATE INDEX IF NOT EXISTS ix_stock_movements_created
    ON stock_movements (created_at DESC, id DESC);

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS unit_cost NUMERIC(12, 2);
