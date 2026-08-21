CREATE TABLE IF NOT EXISTS product_media (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    url VARCHAR(2000) NOT NULL,
    source_url VARCHAR(2000),
    position INTEGER NOT NULL CHECK (position >= 0),
    alt_text VARCHAR(500),
    source VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_product_media_type CHECK (type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT ck_product_media_source CHECK (source IN ('CJ', 'MANUAL'))
);

CREATE INDEX IF NOT EXISTS ix_product_media_product_position
    ON product_media (product_id, position, id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_product_media_cj_source_url
    ON product_media (product_id, source, source_url)
    WHERE source = 'CJ' AND source_url IS NOT NULL;

ALTER TABLE product_variants
    ADD COLUMN IF NOT EXISTS raw_variant_key VARCHAR(500);
