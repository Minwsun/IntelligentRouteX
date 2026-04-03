CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE quotes ADD COLUMN IF NOT EXISTS public_id VARCHAR(64);
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS customer_public_id VARCHAR(64);
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS straight_line_distance_km DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS public_id VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_public_id VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS merchant_public_id VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS actual_fee NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS promised_eta_minutes INTEGER NOT NULL DEFAULT 30;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS assigned_driver_public_id VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS decision_trace_id VARCHAR(128);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS pickup_started_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS picked_up_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS dropoff_started_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS failed_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS pickup_geom geometry(Point, 4326);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS dropoff_geom geometry(Point, 4326);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE orders ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE order_status_history ADD COLUMN IF NOT EXISTS public_id VARCHAR(64);
ALTER TABLE order_status_history ADD COLUMN IF NOT EXISTS order_public_id VARCHAR(64);

ALTER TABLE driver_sessions ADD COLUMN IF NOT EXISTS public_id VARCHAR(64);
ALTER TABLE driver_sessions ADD COLUMN IF NOT EXISTS driver_public_id VARCHAR(64);
ALTER TABLE driver_sessions ADD COLUMN IF NOT EXISTS last_lat DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE driver_sessions ADD COLUMN IF NOT EXISTS last_lng DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE driver_sessions ADD COLUMN IF NOT EXISTS active_offer_public_id VARCHAR(64);
ALTER TABLE driver_sessions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE driver_sessions ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE driver_locations ADD COLUMN IF NOT EXISTS public_id VARCHAR(64);
ALTER TABLE driver_locations ADD COLUMN IF NOT EXISTS driver_public_id VARCHAR(64);
ALTER TABLE driver_locations ADD COLUMN IF NOT EXISTS geom geometry(Point, 4326);

ALTER TABLE offer_batches ADD COLUMN IF NOT EXISTS public_id VARCHAR(64);
ALTER TABLE offer_batches ADD COLUMN IF NOT EXISTS order_public_id VARCHAR(64);
ALTER TABLE offer_batches ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE offer_batches ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE driver_offers ADD COLUMN IF NOT EXISTS public_id VARCHAR(64);
ALTER TABLE driver_offers ADD COLUMN IF NOT EXISTS offer_batch_public_id VARCHAR(64);
ALTER TABLE driver_offers ADD COLUMN IF NOT EXISTS order_public_id VARCHAR(64);
ALTER TABLE driver_offers ADD COLUMN IF NOT EXISTS driver_public_id VARCHAR(64);
ALTER TABLE driver_offers ADD COLUMN IF NOT EXISTS service_tier VARCHAR(32) DEFAULT 'instant';
ALTER TABLE driver_offers ADD COLUMN IF NOT EXISTS borrowed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE driver_offers ADD COLUMN IF NOT EXISTS rationale TEXT;
ALTER TABLE driver_offers ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE driver_offers ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE offer_decisions ADD COLUMN IF NOT EXISTS public_id VARCHAR(96);
ALTER TABLE offer_decisions ADD COLUMN IF NOT EXISTS offer_public_id VARCHAR(64);
ALTER TABLE offer_decisions ADD COLUMN IF NOT EXISTS order_public_id VARCHAR(64);
ALTER TABLE offer_decisions ADD COLUMN IF NOT EXISTS reservation_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE order_reservations ADD COLUMN IF NOT EXISTS public_id VARCHAR(64);
ALTER TABLE order_reservations ADD COLUMN IF NOT EXISTS order_public_id VARCHAR(64);
ALTER TABLE order_reservations ADD COLUMN IF NOT EXISTS offer_batch_public_id VARCHAR(64);
ALTER TABLE order_reservations ADD COLUMN IF NOT EXISTS driver_public_id VARCHAR(64);
ALTER TABLE order_reservations ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE order_reservations ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE assignments ADD COLUMN IF NOT EXISTS public_id VARCHAR(64);
ALTER TABLE assignments ADD COLUMN IF NOT EXISTS order_public_id VARCHAR(64);
ALTER TABLE assignments ADD COLUMN IF NOT EXISTS driver_public_id VARCHAR(64);
ALTER TABLE assignments ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE assignments ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE delivery_tasks ADD COLUMN IF NOT EXISTS public_id VARCHAR(64);
ALTER TABLE delivery_tasks ADD COLUMN IF NOT EXISTS order_public_id VARCHAR(64);
ALTER TABLE delivery_tasks ADD COLUMN IF NOT EXISTS driver_public_id VARCHAR(64);
ALTER TABLE delivery_tasks ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE delivery_tasks ADD COLUMN IF NOT EXISTS status_reason VARCHAR(255);
ALTER TABLE delivery_tasks ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE tracking_events ADD COLUMN IF NOT EXISTS public_id VARCHAR(64);
ALTER TABLE tracking_events ADD COLUMN IF NOT EXISTS order_public_id VARCHAR(64);
ALTER TABLE tracking_events ADD COLUMN IF NOT EXISTS driver_public_id VARCHAR(64);

CREATE TABLE IF NOT EXISTS auth_role_bindings (
    id UUID PRIMARY KEY,
    auth_user_id UUID,
    role_key VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS merchant_accounts (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    merchant_id UUID,
    account_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS merchant_branch_users (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    merchant_branch_id UUID,
    auth_user_id UUID,
    role_key VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS service_regions (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    geom geometry(MultiPolygon, 4326),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS zone_cells_h3 (
    id UUID PRIMARY KEY,
    cell_id VARCHAR(32) NOT NULL,
    service_region_public_id VARCHAR(64),
    resolution INTEGER NOT NULL,
    centroid_geom geometry(Point, 4326),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS order_items (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    order_public_id VARCHAR(64) NOT NULL,
    sku_code VARCHAR(128),
    display_name VARCHAR(255),
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_price NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS order_sla_windows (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    order_public_id VARCHAR(64) NOT NULL,
    promised_at TIMESTAMPTZ,
    latest_assign_at TIMESTAMPTZ,
    latest_pickup_at TIMESTAMPTZ,
    latest_dropoff_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS order_cancellations (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    order_public_id VARCHAR(64) NOT NULL,
    cancelled_by VARCHAR(32) NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS driver_presence_snapshots (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    driver_public_id VARCHAR(64) NOT NULL,
    available BOOLEAN NOT NULL,
    h3_cell_id VARCHAR(32),
    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS driver_cooldowns (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    driver_public_id VARCHAR(64) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS driver_route_snapshots (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    driver_public_id VARCHAR(64) NOT NULL,
    order_public_id VARCHAR(64),
    payload_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS wallet_accounts (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    owner_type VARCHAR(32) NOT NULL,
    owner_public_id VARCHAR(64) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    available_balance NUMERIC(18,2) NOT NULL DEFAULT 0,
    pending_balance NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    owner_type VARCHAR(32) NOT NULL,
    owner_public_id VARCHAR(64) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    amount NUMERIC(18,2) NOT NULL,
    balance_after NUMERIC(18,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reference_type VARCHAR(64),
    reference_public_id VARCHAR(64),
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS payment_intents (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    order_public_id VARCHAR(64),
    customer_public_id VARCHAR(64),
    amount NUMERIC(18,2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'VND',
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS payment_attempts (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    payment_intent_public_id VARCHAR(64) NOT NULL,
    gateway_code VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    response_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS refunds (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    order_public_id VARCHAR(64),
    payment_intent_public_id VARCHAR(64),
    amount NUMERIC(18,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    owner_type VARCHAR(32) NOT NULL,
    owner_public_id VARCHAR(64) NOT NULL,
    account_code VARCHAR(64) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    amount NUMERIC(18,2) NOT NULL,
    reference_type VARCHAR(64),
    reference_public_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS merchant_settlements (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    merchant_public_id VARCHAR(64) NOT NULL,
    settlement_status VARCHAR(32) NOT NULL,
    gross_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS driver_payouts (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    driver_public_id VARCHAR(64) NOT NULL,
    payout_status VARCHAR(32) NOT NULL,
    gross_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS idempotency_records (
    id UUID PRIMARY KEY,
    scope VARCHAR(128) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    response_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    topic_key VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_public_id VARCHAR(64),
    event_type VARCHAR(128) NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);
