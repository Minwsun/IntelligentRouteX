ALTER TABLE idempotency_records
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED';

ALTER TABLE idempotency_records
    ADD COLUMN IF NOT EXISTS claim_token VARCHAR(128) NOT NULL DEFAULT '';

ALTER TABLE idempotency_records
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

UPDATE idempotency_records
   SET status = COALESCE(status, 'COMPLETED'),
       completed_at = COALESCE(completed_at, created_at)
 WHERE status IS NULL
    OR completed_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_idempotency_records_status_created
    ON idempotency_records(status, created_at DESC);
