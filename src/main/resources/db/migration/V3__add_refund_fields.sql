-- Add refund tracking fields to orders table
ALTER TABLE orders ADD COLUMN IF NOT EXISTS refund_status VARCHAR(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS refund_amount DECIMAL(10,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS refund_reference VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS refunded_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS refund_reason TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;

-- Add refund tracking to payment_transactions
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS refund_id VARCHAR(100);
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS is_refunded BOOLEAN DEFAULT FALSE;
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS refunded_at TIMESTAMP;

-- Create refund_transactions table for audit trail
CREATE TABLE IF NOT EXISTS refund_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    transaction_id VARCHAR(100) NOT NULL,
    refund_type VARCHAR(20) NOT NULL CHECK (refund_type IN ('VOID', 'REFUND')),
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'EGP',
    status VARCHAR(50) NOT NULL CHECK (status IN ('initiated', 'pending', 'completed', 'failed')),
    paymob_refund_id VARCHAR(100),
    reason TEXT,
    initiated_by UUID REFERENCES users(id),
    paymob_response JSONB,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refund_order_id ON refund_transactions(order_id);
CREATE INDEX IF NOT EXISTS idx_refund_transaction_id ON refund_transactions(transaction_id);
CREATE INDEX IF NOT EXISTS idx_refund_status ON refund_transactions(status);

-- Add comments for clarity
COMMENT ON TABLE refund_transactions IS 'Audit trail for all refund and void operations';
COMMENT ON COLUMN refund_transactions.refund_type IS 'VOID for unsettled, REFUND for settled transactions';


