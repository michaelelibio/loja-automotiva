ALTER TABLE orders ADD COLUMN IF NOT EXISTS assisted_quote JSONB;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS request_key VARCHAR(36);
CREATE UNIQUE INDEX IF NOT EXISTS uk_orders_request_key ON orders(request_key);
CREATE TABLE IF NOT EXISTS manual_supplier_order_refs (
    fulfillment_id BIGINT NOT NULL REFERENCES order_fulfillments(id) ON DELETE CASCADE,
    supplier_order_id VARCHAR(30) NOT NULL UNIQUE,
    PRIMARY KEY (fulfillment_id, supplier_order_id)
);
