-- Additive schema only. Existing catalog rows and supplier constraints are preserved.
ALTER TABLE products ADD COLUMN IF NOT EXISTS supplier_cost_currency VARCHAR(3);
ALTER TABLE products ADD COLUMN IF NOT EXISTS supplier_metadata JSONB;
ALTER TABLE product_variants ADD COLUMN IF NOT EXISTS supplier_stock_quantity INTEGER;

CREATE TABLE IF NOT EXISTS aliexpress_authorizations (
    id VARCHAR(64) PRIMARY KEY,
    access_ciphertext VARCHAR(4096) NOT NULL,
    refresh_ciphertext VARCHAR(4096),
    access_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    refresh_expires_at TIMESTAMP WITH TIME ZONE,
    version BIGINT
);
CREATE TABLE IF NOT EXISTS aliexpress_oauth_states (
    id VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE
);
