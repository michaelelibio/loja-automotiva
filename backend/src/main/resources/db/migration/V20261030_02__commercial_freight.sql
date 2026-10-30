-- Additive, manually applied before controlled startup. No tariffs or prices are seeded.
CREATE TABLE IF NOT EXISTS commercial_freight_tables (
 id BIGSERIAL PRIMARY KEY,
 document JSONB NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 published_at TIMESTAMP WITH TIME ZONE,
 enabled BOOLEAN NOT NULL DEFAULT FALSE
);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS commercial_shipping JSONB;
