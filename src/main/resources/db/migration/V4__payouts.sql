-- Payouts schema for vendor disbursements (Paymob Egypt)

-- 1) Enum for payout lifecycle
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payout_status') THEN
        CREATE TYPE payout_status AS ENUM ('pending','processing','completed','failed','cancelled');
    END IF;
END$$;

-- 2) vendor_payouts main table
CREATE TABLE IF NOT EXISTS vendor_payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_id UUID NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    gross_amount DECIMAL(10,2) NOT NULL,
    commission_amount DECIMAL(10,2) NOT NULL,
    net_amount DECIMAL(10,2) NOT NULL,
    status payout_status DEFAULT 'pending',
    scheduled_for TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    paymob_payout_id VARCHAR(255),
    paymob_response JSONB,
    bank_account_number VARCHAR(50),
    bank_routing_number VARCHAR(50),
    bank_account_holder_name VARCHAR(255),
    bank_name VARCHAR(255),
    retry_count INTEGER DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_vendor_payouts_vendor ON vendor_payouts(vendor_id);
CREATE INDEX IF NOT EXISTS idx_vendor_payouts_order ON vendor_payouts(order_id);
CREATE INDEX IF NOT EXISTS idx_vendor_payouts_status ON vendor_payouts(status);
CREATE INDEX IF NOT EXISTS idx_vendor_payouts_scheduled ON vendor_payouts(scheduled_for);

-- 3) Optional webhook log table for payout callbacks
CREATE TABLE IF NOT EXISTS payout_webhook_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payout_id UUID REFERENCES vendor_payouts(id) ON DELETE SET NULL,
    paymob_payout_id VARCHAR(255),
    event_type VARCHAR(100),
    payload JSONB,
    processed BOOLEAN DEFAULT FALSE,
    processed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payout_webhook_logs_processed ON payout_webhook_logs(processed, created_at);


