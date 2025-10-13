-- Delivery Integration Schema for Fincart API
-- V6__delivery_integration.sql

-- Pickup locations synced from Fincart
CREATE TABLE IF NOT EXISTS pickup_locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_id UUID REFERENCES vendors(id) ON DELETE CASCADE,
    fincart_location_id VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    area VARCHAR(100) NOT NULL,
    address TEXT NOT NULL,
    contact_person VARCHAR(255),
    contact_phone VARCHAR(20),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Cities and areas for address validation
CREATE TABLE IF NOT EXISTS delivery_areas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    city VARCHAR(100) NOT NULL,
    area VARCHAR(100) NOT NULL,
    is_available BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(city, area)
);

-- Delivery tracking for each order
CREATE TABLE IF NOT EXISTS deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    vendor_id UUID NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    fincart_order_id VARCHAR(100) NOT NULL UNIQUE,
    fincart_order_code VARCHAR(100),
    tracking_number VARCHAR(100),
    return_tracking_number VARCHAR(100),
    courier VARCHAR(100),
    courier_logo VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    sub_status VARCHAR(100),
    rejection_reason TEXT,
    support_note TEXT,
    invoiced BOOLEAN DEFAULT FALSE,
    invoiced_at TIMESTAMP,
    pickup_location_id UUID REFERENCES pickup_locations(id),
    payment_type VARCHAR(50) NOT NULL, -- 'with_cash_collection' or 'without_cash_collection'
    amount_cents INTEGER DEFAULT 0,
    service_type VARCHAR(50) DEFAULT 'standard', -- 'standard' or 'same-day'
    package_type VARCHAR(50) DEFAULT 'parcel',
    no_of_items INTEGER DEFAULT 1,
    description TEXT,
    reference_number VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Delivery status logs for tracking history
CREATE TABLE IF NOT EXISTS delivery_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id UUID NOT NULL REFERENCES deliveries(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    sub_status VARCHAR(100),
    rejection_reason TEXT,
    support_note TEXT,
    tracking_number VARCHAR(100),
    return_tracking_number VARCHAR(100),
    courier VARCHAR(100),
    courier_logo VARCHAR(500),
    invoiced BOOLEAN DEFAULT FALSE,
    invoiced_at TIMESTAMP,
    logged_at TIMESTAMP DEFAULT NOW(),
    notes TEXT
);

-- Webhook logs for audit trail
CREATE TABLE IF NOT EXISTS delivery_webhook_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fincart_order_id VARCHAR(100),
    event_type VARCHAR(100),
    payload JSONB NOT NULL,
    processed BOOLEAN DEFAULT FALSE,
    processed_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Fincart API tokens and configuration
CREATE TABLE IF NOT EXISTS fincart_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    access_token TEXT NOT NULL,
    token_expires_at TIMESTAMP NOT NULL,
    webhook_secret VARCHAR(255),
    base_url VARCHAR(255) DEFAULT 'https://api.fincart.com',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_pickup_locations_vendor ON pickup_locations(vendor_id);
CREATE INDEX IF NOT EXISTS idx_pickup_locations_fincart ON pickup_locations(fincart_location_id);
CREATE INDEX IF NOT EXISTS idx_delivery_areas_city ON delivery_areas(city);
CREATE INDEX IF NOT EXISTS idx_deliveries_order ON deliveries(order_id);
CREATE INDEX IF NOT EXISTS idx_deliveries_vendor ON deliveries(vendor_id);
CREATE INDEX IF NOT EXISTS idx_deliveries_fincart ON deliveries(fincart_order_id);
CREATE INDEX IF NOT EXISTS idx_deliveries_status ON deliveries(status);
CREATE INDEX IF NOT EXISTS idx_delivery_logs_delivery ON delivery_logs(delivery_id);
CREATE INDEX IF NOT EXISTS idx_delivery_logs_status ON delivery_logs(status);
CREATE INDEX IF NOT EXISTS idx_delivery_webhook_logs_processed ON delivery_webhook_logs(processed, created_at);
CREATE INDEX IF NOT EXISTS idx_delivery_webhook_logs_fincart ON delivery_webhook_logs(fincart_order_id);

-- Comments for documentation
COMMENT ON TABLE pickup_locations IS 'Vendor pickup locations synced from Fincart API';
COMMENT ON TABLE delivery_areas IS 'Available delivery cities and areas for address validation';
COMMENT ON TABLE deliveries IS 'Individual delivery records for each order/vendor combination';
COMMENT ON TABLE delivery_logs IS 'Status change history for each delivery';
COMMENT ON TABLE delivery_webhook_logs IS 'Audit trail for Fincart webhook events';
COMMENT ON TABLE fincart_config IS 'Fincart API configuration and tokens';

COMMENT ON COLUMN deliveries.payment_type IS 'with_cash_collection for COD, without_cash_collection for prepaid';
COMMENT ON COLUMN deliveries.amount_cents IS 'COD amount in cents, 0 for prepaid orders';
COMMENT ON COLUMN deliveries.service_type IS 'standard or same-day delivery';
COMMENT ON COLUMN deliveries.package_type IS 'parcel, document, or other package types';
