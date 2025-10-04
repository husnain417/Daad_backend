-- Payments schema updates for Paymob integration

-- Add payment tracking columns to orders table
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_provider VARCHAR(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS transaction_id VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS failure_reason TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_metadata JSONB;

-- Create payment transactions audit table
CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    order_id UUID REFERENCES orders(id) ON DELETE CASCADE,
    payment_provider VARCHAR(50) NOT NULL,
    transaction_id VARCHAR(100),
    payment_reference VARCHAR(100),
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'EGP',
    status VARCHAR(50) NOT NULL,
    provider_response JSONB,
    webhook_data JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_payment_transactions_order_id ON payment_transactions(order_id);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_transaction_id ON payment_transactions(transaction_id);
CREATE INDEX IF NOT EXISTS idx_orders_payment_reference ON orders(payment_reference);


