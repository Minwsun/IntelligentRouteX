ALTER TABLE offer_batches ADD COLUMN IF NOT EXISTS wave_number INTEGER NOT NULL DEFAULT 1;
ALTER TABLE offer_batches ADD COLUMN IF NOT EXISTS previous_batch_public_id VARCHAR(64);
ALTER TABLE offer_batches ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ;
ALTER TABLE offer_batches ADD COLUMN IF NOT EXISTS close_reason VARCHAR(64);

ALTER TABLE order_reservations ADD COLUMN IF NOT EXISTS accepted_offer_public_id VARCHAR(64);

ALTER TABLE offer_decisions ADD COLUMN IF NOT EXISTS offer_batch_public_id VARCHAR(64);
