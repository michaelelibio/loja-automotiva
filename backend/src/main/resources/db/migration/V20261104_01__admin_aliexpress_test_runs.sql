-- Additive only: never change historical orders, payments or fulfillment.
CREATE TABLE IF NOT EXISTS admin_aliexpress_test_runs (
    order_id BIGINT PRIMARY KEY,
    simulated_payment_at TIMESTAMP WITH TIME ZONE,
    simulated_payment_by VARCHAR(255),
    simulated_supplier_at TIMESTAMP WITH TIME ZONE,
    simulated_supplier_by VARCHAR(255),
    simulated_supplier_id VARCHAR(255),
    real_key VARCHAR(100) UNIQUE,
    fixture_digest VARCHAR(64),
    real_status VARCHAR(255),
    submitted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE,
    submitted_by VARCHAR(255),
    supplier_order_ids JSONB
);
