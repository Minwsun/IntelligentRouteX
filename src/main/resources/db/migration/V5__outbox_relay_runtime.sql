ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ;

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS last_error VARCHAR(500);

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(128);

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ;

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(64);

UPDATE outbox_events
   SET next_attempt_at = created_at
 WHERE next_attempt_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_events_claimable
    ON outbox_events(status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS idx_outbox_events_claimed_at
    ON outbox_events(claimed_at);
