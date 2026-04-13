CREATE TABLE IF NOT EXISTS order_lifecycle_facts (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL UNIQUE,
    order_id UUID NOT NULL,
    order_public_id VARCHAR(64) NOT NULL,
    fact_type VARCHAR(64) NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_public_id VARCHAR(64),
    idempotency_key VARCHAR(128),
    correlation_id VARCHAR(128),
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    recorded_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_order_lifecycle_facts_order_time
    ON order_lifecycle_facts (order_public_id, recorded_at ASC, public_id ASC);
