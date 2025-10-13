-- Payout improvements: cancellation fields and webhook processed flags

ALTER TABLE vendor_payouts ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(500);
ALTER TABLE vendor_payouts ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;

ALTER TABLE payout_webhook_logs ADD COLUMN IF NOT EXISTS processed BOOLEAN DEFAULT FALSE;
ALTER TABLE payout_webhook_logs ADD COLUMN IF NOT EXISTS processed_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_payout_webhook_logs_lookup 
ON payout_webhook_logs(paymob_payout_id, processed) WHERE processed = TRUE;


