-- Prevent a provider order from being attached to multiple local transactions.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_provider_order
    ON payment_transaction(provider, provider_order_id)
    WHERE provider_order_id IS NOT NULL;

-- Speed up operational review of webhook events that need attention.
CREATE INDEX IF NOT EXISTS ix_payment_webhook_status_received
    ON payment_webhook_event(processing_status, received_at DESC);

-- Keep rejected webhook evidence queryable without exposing payment payloads in logs.
CREATE INDEX IF NOT EXISTS ix_payment_transaction_order_status
    ON payment_transaction(order_id, status, created_at DESC);
