CREATE TABLE IF NOT EXISTS auth_users (
    id UUID PRIMARY KEY,
    role VARCHAR(32) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS customer_profiles (
    id UUID PRIMARY KEY,
    auth_user_id UUID,
    display_name VARCHAR(255),
    default_address_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS driver_profiles (
    id UUID PRIMARY KEY,
    auth_user_id UUID,
    display_name VARCHAR(255),
    vehicle_type VARCHAR(32),
    home_region_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS driver_devices (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    platform VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS driver_sessions (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMPTZ NOT NULL,
    active_offer_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS driver_locations (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS driver_shifts (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS merchants (
    id UUID PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS merchant_branches (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    branch_name VARCHAR(255),
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    region_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS merchant_prep_profiles (
    id UUID PRIMARY KEY,
    merchant_branch_id UUID NOT NULL,
    p50_prep_minutes DOUBLE PRECISION,
    p90_prep_minutes DOUBLE PRECISION,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS addresses (
    id UUID PRIMARY KEY,
    owner_type VARCHAR(32) NOT NULL,
    owner_id UUID NOT NULL,
    label VARCHAR(64),
    line1 VARCHAR(255),
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    region_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS quotes (
    id UUID PRIMARY KEY,
    customer_id UUID,
    service_tier VARCHAR(32) NOT NULL,
    quoted_fee DOUBLE PRECISION NOT NULL,
    eta_minutes INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY,
    customer_id UUID,
    merchant_id UUID,
    service_tier VARCHAR(32) NOT NULL,
    pickup_region_id VARCHAR(64),
    dropoff_region_id VARCHAR(64),
    pickup_lat DOUBLE PRECISION,
    pickup_lng DOUBLE PRECISION,
    dropoff_lat DOUBLE PRECISION,
    dropoff_lng DOUBLE PRECISION,
    quoted_fee DOUBLE PRECISION NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    assigned_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS order_status_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(255),
    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS offer_batches (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    service_tier VARCHAR(32) NOT NULL,
    fanout INTEGER NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS driver_offers (
    id UUID PRIMARY KEY,
    offer_batch_id UUID NOT NULL,
    order_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    acceptance_probability DOUBLE PRECISION NOT NULL,
    deadhead_km DOUBLE PRECISION NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS offer_decisions (
    id UUID PRIMARY KEY,
    offer_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(255),
    decided_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS order_reservations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    offer_batch_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    reservation_version BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    reserved_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS assignments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    assignment_mode VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS delivery_tasks (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS tracking_events (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    driver_id UUID,
    event_type VARCHAR(64) NOT NULL,
    payload_json TEXT,
    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS dispatch_decisions (
    id UUID PRIMARY KEY,
    run_id VARCHAR(128) NOT NULL,
    order_id UUID,
    driver_id UUID,
    selection_bucket VARCHAR(64),
    score DOUBLE PRECISION,
    deadhead_km DOUBLE PRECISION,
    dispatch_latency_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dispatch_outcomes (
    id UUID PRIMARY KEY,
    run_id VARCHAR(128) NOT NULL,
    order_id UUID,
    outcome VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS feature_snapshots (
    id UUID PRIMARY KEY,
    run_id VARCHAR(128) NOT NULL,
    topic VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS model_inferences (
    id UUID PRIMARY KEY,
    run_id VARCHAR(128) NOT NULL,
    model_key VARCHAR(128) NOT NULL,
    model_version VARCHAR(128),
    payload_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS run_reports (
    id UUID PRIMARY KEY,
    run_id VARCHAR(128) NOT NULL,
    scenario_name VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS benchmark_manifests (
    id UUID PRIMARY KEY,
    run_id VARCHAR(128) NOT NULL,
    scenario_name VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
