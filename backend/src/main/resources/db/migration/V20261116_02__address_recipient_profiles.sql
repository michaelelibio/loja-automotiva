-- Manual additive migration. Apply transactionally before deploying the mapped entity.
-- Personal values are never backfilled from historical orders.
CREATE TABLE IF NOT EXISTS address_recipient_profiles (
    address_id BIGINT PRIMARY KEY REFERENCES addresses(id) ON DELETE CASCADE,
    binding VARCHAR(64) NOT NULL,
    ciphertext VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
