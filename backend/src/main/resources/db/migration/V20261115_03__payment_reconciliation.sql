-- Apply after V20261115_02__payment_reversals.sql and before deploying the matching application.
-- Recovery never creates payments/preferences or sends supplier payment commands.
CREATE TABLE IF NOT EXISTS payment_reconciliations (
    payment_id BIGINT PRIMARY KEY REFERENCES payments(id) ON DELETE CASCADE,
    lease_token VARCHAR(36),
    next_check_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_checked_at TIMESTAMP WITH TIME ZONE,
    failure_count INTEGER NOT NULL DEFAULT 0,
    last_failure_code VARCHAR(40),
    review_required BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS ix_payment_reconciliations_due ON payment_reconciliations(next_check_at,payment_id);
CREATE INDEX IF NOT EXISTS ix_payments_recovery ON payments(created_at,id) WHERE method='MERCADO_PAGO' AND external_reference IS NOT NULL AND provider_preference_id IS NOT NULL;
