CREATE UNIQUE INDEX IF NOT EXISTS uq_quotes_public_id ON quotes(public_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_public_id ON orders(public_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_order_status_history_public_id ON order_status_history(public_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_driver_sessions_driver_public_id ON driver_sessions(driver_public_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_offer_batches_public_id ON offer_batches(public_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_driver_offers_public_id ON driver_offers(public_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_offer_decisions_public_id ON offer_decisions(public_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_order_reservations_order_public_id ON order_reservations(order_public_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_accounts_owner ON wallet_accounts(owner_type, owner_public_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_transactions_public_id ON wallet_transactions(public_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_idempotency_records_scope_actor_key ON idempotency_records(scope, actor_id, idempotency_key);
CREATE UNIQUE INDEX IF NOT EXISTS uq_outbox_events_public_id ON outbox_events(public_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_zone_cells_h3_cell_id ON zone_cells_h3(cell_id);

CREATE INDEX IF NOT EXISTS idx_orders_status_created_at ON orders(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_customer_public_id ON orders(customer_public_id);
CREATE INDEX IF NOT EXISTS idx_driver_offers_driver_status_expires ON driver_offers(driver_public_id, status, expires_at);
CREATE INDEX IF NOT EXISTS idx_driver_offers_order_public_id ON driver_offers(order_public_id);
CREATE INDEX IF NOT EXISTS idx_offer_batches_order_public_id_created ON offer_batches(order_public_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tracking_events_order_public_id ON tracking_events(order_public_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_driver_locations_driver_public_id_recorded ON driver_locations(driver_public_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_driver_cooldowns_driver_public_id_expires ON driver_cooldowns(driver_public_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_owner_created ON wallet_transactions(owner_type, owner_public_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_outbox_events_status_created ON outbox_events(status, created_at);

CREATE INDEX IF NOT EXISTS idx_orders_pickup_geom ON orders USING GIST(pickup_geom);
CREATE INDEX IF NOT EXISTS idx_orders_dropoff_geom ON orders USING GIST(dropoff_geom);
CREATE INDEX IF NOT EXISTS idx_driver_locations_geom ON driver_locations USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_zone_cells_h3_centroid_geom ON zone_cells_h3 USING GIST(centroid_geom);
