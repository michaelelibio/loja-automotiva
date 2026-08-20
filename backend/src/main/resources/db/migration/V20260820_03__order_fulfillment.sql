CREATE TABLE IF NOT EXISTS order_fulfillments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    supplier_order_id VARCHAR(200),
    supplier_shipment_order_id VARCHAR(200),
    external_reference VARCHAR(50) NOT NULL,
    processing_token VARCHAR(36),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    processing_started_at TIMESTAMP WITH TIME ZONE,
    created_externally_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_order_fulfillments_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT uk_order_fulfillments_order UNIQUE (order_id),
    CONSTRAINT uk_order_fulfillments_reference UNIQUE (external_reference),
    CONSTRAINT uk_order_fulfillments_supplier_order UNIQUE (supplier_order_id),
    CONSTRAINT ck_order_fulfillments_status CHECK
        (status IN ('NOT_REQUIRED', 'PENDING', 'PROCESSING', 'CREATED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_order_fulfillments_status ON order_fulfillments(status);
