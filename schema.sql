-- =====================================================
-- Complete PostgreSQL Schema for E-commerce Platform
-- Generated: 2025-10-25
-- Database: Supabase PostgreSQL
-- =====================================================

-- Enable necessary extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =====================================================
-- ENUM TYPES
-- =====================================================

CREATE TYPE user_role AS ENUM ('customer', 'admin', 'vendor');
CREATE TYPE auth_provider AS ENUM ('local', 'google');
CREATE TYPE address_type AS ENUM ('home', 'work', 'other');
CREATE TYPE business_type AS ENUM ('individual', 'business', 'company');
CREATE TYPE vendor_status AS ENUM ('pending', 'approved', 'rejected', 'suspended');
CREATE TYPE product_gender AS ENUM ('Men', 'Women', 'Unisex', 'Male', 'Female', 'None');
CREATE TYPE product_status AS ENUM ('none', 'draft', 'awaiting_approval', 'approved', 'rejected');
CREATE TYPE page_type AS ENUM ('home', 'mens', 'womens');
CREATE TYPE view_type AS ENUM ('web', 'mobile');
CREATE TYPE review_status AS ENUM ('pending', 'approved', 'rejected');
CREATE TYPE voucher_type AS ENUM ('percentage', 'fixed');
CREATE TYPE voucher_applicable AS ENUM ('all', 'category', 'brand', 'vendor', 'first-order', 'student');
CREATE TYPE payment_method AS ENUM ('cash-on-delivery', 'bank-transfer', 'card');
CREATE TYPE payment_status AS ENUM ('pending', 'paid', 'failed');
CREATE TYPE order_status AS ENUM ('pending', 'confirmed', 'processing', 'shipped', 'delivered', 'cancelled');
CREATE TYPE payout_status AS ENUM ('pending', 'processing', 'completed', 'failed', 'cancelled');

-- =====================================================
-- CORE USER TABLES
-- =====================================================

-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255),
    auth_provider auth_provider DEFAULT 'local',
    google_id VARCHAR(255),
    otp VARCHAR(10),
    otp_expires TIMESTAMP,
    is_verified BOOLEAN DEFAULT FALSE,
    profile_pic_url TEXT,
    role user_role DEFAULT 'customer',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- User addresses table
CREATE TABLE user_addresses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    type address_type DEFAULT 'home',
    full_name VARCHAR(255),
    address_line1 TEXT,
    address_line2 TEXT,
    city VARCHAR(100),
    state VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100),
    phone_number VARCHAR(20),
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- =====================================================
-- CATALOG TABLES
-- =====================================================

-- Categories table
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) UNIQUE NOT NULL,
    description TEXT,
    image_url TEXT,
    image_public_id VARCHAR(255),
    parent_category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    level INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- =====================================================
-- VENDOR TABLES
-- =====================================================

-- Vendors table
CREATE TABLE vendors (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    business_name VARCHAR(255) NOT NULL,
    business_type business_type NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    business_address_line1 TEXT NOT NULL,
    business_address_line2 TEXT,
    business_city VARCHAR(100) NOT NULL,
    business_state VARCHAR(100) NOT NULL,
    business_postal_code VARCHAR(20) NOT NULL,
    business_country VARCHAR(100) DEFAULT 'Pakistan',
    description TEXT,
    logo_url TEXT,
    logo_public_id VARCHAR(255),
    status vendor_status DEFAULT 'pending',
    approved_by UUID REFERENCES users(id),
    approved_at TIMESTAMP,
    bank_account_number VARCHAR(50),
    bank_routing_number VARCHAR(50),
    bank_account_holder_name VARCHAR(255),
    bank_name VARCHAR(255),
    tax_id VARCHAR(50),
    return_window INTEGER DEFAULT 30,
    shipping_policy TEXT,
    return_policy TEXT,
    commission DECIMAL(10,2) DEFAULT 10.00,
    rating_average DECIMAL(3,2) DEFAULT 0.00,
    rating_count INTEGER DEFAULT 0,
    profile_completed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Pickup locations for vendors
CREATE TABLE pickup_locations (
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

-- =====================================================
-- PRODUCT TABLES
-- =====================================================

-- Products table
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL CHECK (price >= 0),
    reference_id VARCHAR(255),
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    vendor_id UUID REFERENCES vendors(id) ON DELETE CASCADE,
    gender product_gender DEFAULT 'None',
    age_range VARCHAR(50),
    total_stock INTEGER DEFAULT 0 CHECK (total_stock >= 0),
    discount_percentage DECIMAL(5,2) DEFAULT 0 CHECK (discount_percentage >= 0),
    discount_valid_until TIMESTAMP,
    average_rating DECIMAL(3,2) DEFAULT 0 CHECK (average_rating >= 0 AND average_rating <= 5),
    status product_status DEFAULT 'draft',
    is_active BOOLEAN DEFAULT TRUE,
    is_customers_also_bought BOOLEAN DEFAULT FALSE,
    approval_action VARCHAR(50),
    approval_approved_by UUID REFERENCES users(id),
    approval_approved_at TIMESTAMP,
    approval_reason TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Product colors
CREATE TABLE product_colors (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(7),
    is_available BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Product sizes
CREATE TABLE product_sizes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    is_available BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Product inventory
CREATE TABLE product_inventory (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    color VARCHAR(100) NOT NULL,
    color_code VARCHAR(7),
    size VARCHAR(50) NOT NULL,
    stock INTEGER DEFAULT 0 CHECK (stock >= 0),
    is_available BOOLEAN DEFAULT TRUE,
    min_stock_threshold INTEGER DEFAULT 5,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(product_id, color, size)
);

-- Product images
CREATE TABLE product_images (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    color VARCHAR(100),
    url TEXT NOT NULL,
    alt_text TEXT,
    file_id VARCHAR(255),
    is_primary BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Product what's in box
CREATE TABLE product_whats_in_box (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    image_url TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Product related products
CREATE TABLE product_related (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    related_product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(product_id, related_product_id)
);

-- =====================================================
-- REVIEW TABLES
-- =====================================================

-- Reviews
CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    customer_id UUID REFERENCES users(id) ON DELETE CASCADE,
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    title VARCHAR(255),
    comment TEXT,
    is_verified BOOLEAN DEFAULT FALSE,
    status review_status DEFAULT 'pending',
    helpful_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(product_id, customer_id)
);

-- Review images
CREATE TABLE review_images (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    review_id UUID REFERENCES reviews(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    public_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW()
);

-- Review helpful votes
CREATE TABLE review_helpful_votes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    review_id UUID REFERENCES reviews(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(review_id, user_id)
);

-- =====================================================
-- MARKETING & UI TABLES
-- =====================================================

-- Hero images
CREATE TABLE hero_images (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    page_type page_type NOT NULL,
    view_type view_type NOT NULL,
    image_url TEXT NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    storage_provider VARCHAR(50) DEFAULT 'backblaze_b2',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Subscribers
CREATE TABLE subscribers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- =====================================================
-- VOUCHER TABLES
-- =====================================================

-- Vouchers
CREATE TABLE vouchers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(50) UNIQUE NOT NULL,
    type voucher_type NOT NULL,
    value DECIMAL(10,2) NOT NULL CHECK (value >= 0),
    minimum_order DECIMAL(10,2) DEFAULT 0,
    maximum_discount DECIMAL(10,2),
    usage_limit INTEGER,
    used_count INTEGER DEFAULT 0,
    applicable_for voucher_applicable DEFAULT 'all',
    valid_from TIMESTAMP NOT NULL,
    valid_until TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_by UUID REFERENCES users(id) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Voucher applicable items
CREATE TABLE voucher_applicable_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    voucher_id UUID REFERENCES vouchers(id) ON DELETE CASCADE,
    applicable_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- =====================================================
-- CART TABLES (supports both users and guests)
-- =====================================================

-- Carts
CREATE TABLE carts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    cart_id UUID,
    is_guest BOOLEAN DEFAULT FALSE,
    subtotal DECIMAL(10,2) DEFAULT 0,
    tax DECIMAL(10,2) DEFAULT 0,
    shipping DECIMAL(10,2) DEFAULT 0,
    discount DECIMAL(10,2) DEFAULT 0,
    total DECIMAL(10,2) DEFAULT 0,
    shipping_address_type address_type DEFAULT 'home',
    shipping_full_name VARCHAR(255),
    shipping_address_line1 TEXT,
    shipping_address_line2 TEXT,
    shipping_city VARCHAR(100),
    shipping_state VARCHAR(100),
    shipping_postal_code VARCHAR(20),
    shipping_country VARCHAR(100),
    shipping_phone_number VARCHAR(20),
    shipping_email VARCHAR(255),
    estimated_delivery_min_days INTEGER,
    estimated_delivery_max_days INTEGER,
    last_updated TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT check_user_or_guest CHECK (
        (user_id IS NOT NULL AND cart_id IS NULL AND is_guest = FALSE) OR
        (user_id IS NULL AND cart_id IS NOT NULL AND is_guest = TRUE)
    ),
    CONSTRAINT unique_user_cart UNIQUE (user_id),
    CONSTRAINT unique_guest_cart UNIQUE (cart_id)
);

-- Cart items
CREATE TABLE cart_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cart_id UUID REFERENCES carts(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    vendor_id UUID REFERENCES vendors(id) ON DELETE CASCADE,
    color VARCHAR(100) NOT NULL,
    size VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity >= 1),
    price DECIMAL(10,2) NOT NULL CHECK (price >= 0),
    discounted_price DECIMAL(10,2) CHECK (discounted_price >= 0),
    total_price DECIMAL(10,2) NOT NULL CHECK (total_price >= 0),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(cart_id, product_id, vendor_id, color, size)
);

-- Cart applied vouchers
CREATE TABLE cart_applied_vouchers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cart_id UUID REFERENCES carts(id) ON DELETE CASCADE,
    voucher_id UUID REFERENCES vouchers(id) ON DELETE CASCADE,
    discount_amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- =====================================================
-- ORDER TABLES
-- =====================================================

-- Orders
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id),
    customer_email VARCHAR(255) NOT NULL,
    shipping_full_name VARCHAR(255) NOT NULL,
    shipping_address_line1 TEXT NOT NULL,
    shipping_address_line2 TEXT,
    shipping_city VARCHAR(100) NOT NULL,
    shipping_state VARCHAR(100) NOT NULL,
    shipping_postal_code VARCHAR(20) NOT NULL,
    shipping_country VARCHAR(100) NOT NULL,
    shipping_phone_number VARCHAR(20) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL CHECK (subtotal >= 0),
    discount DECIMAL(10,2) DEFAULT 0 CHECK (discount >= 0),
    discount_code VARCHAR(50),
    shipping_charges DECIMAL(10,2) DEFAULT 0 CHECK (shipping_charges >= 0),
    total DECIMAL(10,2) NOT NULL CHECK (total >= 0),
    points_used INTEGER DEFAULT 0 CHECK (points_used >= 0),
    points_earned INTEGER DEFAULT 0 CHECK (points_earned >= 0),
    payment_method payment_method NOT NULL,
    payment_status payment_status DEFAULT 'pending',
    payment_receipt_url TEXT,
    payment_receipt_public_id VARCHAR(255),
    payment_receipt_uploaded BOOLEAN DEFAULT FALSE,
    payment_provider VARCHAR(50),
    payment_reference VARCHAR(100),
    transaction_id VARCHAR(100),
    paid_at TIMESTAMP,
    failure_reason TEXT,
    payment_metadata JSONB,
    order_status order_status DEFAULT 'pending',
    is_first_order BOOLEAN DEFAULT FALSE,
    tracking_number VARCHAR(255),
    estimated_delivery TIMESTAMP,
    delivered_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancellation_reason TEXT,
    refund_status VARCHAR(50),
    refund_amount DECIMAL(10,2),
    refund_reference VARCHAR(100),
    refunded_at TIMESTAMP,
    refund_reason TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Order items
CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id),
    product_name VARCHAR(255) NOT NULL,
    color VARCHAR(100) NOT NULL,
    size VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity >= 1),
    price DECIMAL(10,2) NOT NULL CHECK (price >= 0),
    created_at TIMESTAMP DEFAULT NOW()
);

-- =====================================================
-- PAYMENT TABLES
-- =====================================================

-- Payment transactions
CREATE TABLE payment_transactions (
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
    refund_id VARCHAR(100),
    is_refunded BOOLEAN DEFAULT FALSE,
    refunded_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Refund transactions
CREATE TABLE refund_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    transaction_id VARCHAR(100) NOT NULL,
    refund_type VARCHAR(20) NOT NULL CHECK (refund_type IN ('VOID', 'REFUND')),
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'PKR',
    status VARCHAR(50) NOT NULL CHECK (status IN ('initiated', 'pending', 'completed', 'failed')),
    paymob_refund_id VARCHAR(100),
    reason TEXT,
    initiated_by VARCHAR(100),
    paymob_response JSONB,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- PAYOUT TABLES
-- =====================================================

-- Vendor payouts
CREATE TABLE vendor_payouts (
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
    cancellation_reason VARCHAR(500),
    cancelled_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Payout webhook logs
CREATE TABLE payout_webhook_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payout_id UUID REFERENCES vendor_payouts(id) ON DELETE SET NULL,
    paymob_payout_id VARCHAR(255),
    event_type VARCHAR(100),
    payload JSONB,
    processed BOOLEAN DEFAULT FALSE,
    processed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

-- =====================================================
-- DELIVERY TABLES (Fincart Integration)
-- =====================================================

-- Delivery areas
CREATE TABLE delivery_areas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    city VARCHAR(100) NOT NULL,
    area VARCHAR(100) NOT NULL,
    is_available BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(city, area)
);

-- Deliveries
CREATE TABLE deliveries (
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
    payment_type VARCHAR(50) NOT NULL,
    amount_cents INTEGER DEFAULT 0,
    service_type VARCHAR(50) DEFAULT 'standard',
    package_type VARCHAR(50) DEFAULT 'parcel',
    no_of_items INTEGER DEFAULT 1,
    description TEXT,
    reference_number VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Delivery logs
CREATE TABLE delivery_logs (
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

-- Delivery webhook logs
CREATE TABLE delivery_webhook_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fincart_order_id VARCHAR(100),
    event_type VARCHAR(100),
    payload JSONB NOT NULL,
    processed BOOLEAN DEFAULT FALSE,
    processed_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Fincart configuration
CREATE TABLE fincart_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    access_token TEXT NOT NULL,
    token_expires_at TIMESTAMP NOT NULL,
    webhook_secret VARCHAR(255),
    base_url VARCHAR(255) DEFAULT 'https://api.fincart.com',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- =====================================================
-- INDEXES FOR PERFORMANCE
-- =====================================================

-- User indexes
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_user_addresses_user_id ON user_addresses(user_id);

-- Category indexes
CREATE INDEX idx_categories_slug ON categories(slug);
CREATE INDEX idx_categories_parent ON categories(parent_category_id);

-- Vendor indexes
CREATE INDEX idx_vendors_user_id ON vendors(user_id);
CREATE INDEX idx_vendors_status ON vendors(status);
CREATE INDEX idx_pickup_locations_vendor ON pickup_locations(vendor_id);
CREATE INDEX idx_pickup_locations_fincart ON pickup_locations(fincart_location_id);

-- Product indexes
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_vendor ON products(vendor_id);
CREATE INDEX idx_products_status ON products(status);
CREATE INDEX idx_products_active ON products(is_active);
CREATE INDEX idx_product_inventory_product ON product_inventory(product_id);
CREATE INDEX idx_product_inventory_color ON product_inventory(color);
CREATE INDEX idx_product_inventory_size ON product_inventory(size);
CREATE INDEX idx_product_inventory_stock ON product_inventory(stock);
CREATE INDEX idx_product_inventory_available ON product_inventory(is_available);
CREATE INDEX idx_product_images_product ON product_images(product_id);

-- Review indexes
CREATE INDEX idx_reviews_product ON reviews(product_id);
CREATE INDEX idx_reviews_customer ON reviews(customer_id);
CREATE INDEX idx_reviews_status ON reviews(status);

-- Cart indexes
CREATE INDEX idx_carts_user ON carts(user_id) WHERE is_guest = false;
CREATE INDEX idx_carts_guest ON carts(cart_id) WHERE is_guest = true;
CREATE INDEX idx_carts_guest_flag ON carts(is_guest);
CREATE INDEX idx_carts_created_at ON carts(created_at);
CREATE INDEX idx_cart_items_cart ON cart_items(cart_id);

-- Order indexes
CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(order_status);
CREATE INDEX idx_orders_payment_status ON orders(payment_status);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_orders_payment_reference ON orders(payment_reference);

-- Payment indexes
CREATE INDEX idx_payment_transactions_order_id ON payment_transactions(order_id);
CREATE INDEX idx_payment_transactions_transaction_id ON payment_transactions(transaction_id);
CREATE INDEX idx_refund_order_id ON refund_transactions(order_id);
CREATE INDEX idx_refund_transaction_id ON refund_transactions(transaction_id);
CREATE INDEX idx_refund_status ON refund_transactions(status);

-- Payout indexes
CREATE INDEX idx_vendor_payouts_vendor ON vendor_payouts(vendor_id);
CREATE INDEX idx_vendor_payouts_order ON vendor_payouts(order_id);
CREATE INDEX idx_vendor_payouts_status ON vendor_payouts(status);
CREATE INDEX idx_vendor_payouts_scheduled ON vendor_payouts(scheduled_for);
CREATE INDEX idx_payout_webhook_logs_processed ON payout_webhook_logs(processed, created_at);
CREATE INDEX idx_payout_webhook_logs_lookup ON payout_webhook_logs(paymob_payout_id, processed) WHERE processed = TRUE;

-- Delivery indexes
CREATE INDEX idx_delivery_areas_city ON delivery_areas(city);
CREATE INDEX idx_deliveries_order ON deliveries(order_id);
CREATE INDEX idx_deliveries_vendor ON deliveries(vendor_id);
CREATE INDEX idx_deliveries_fincart ON deliveries(fincart_order_id);
CREATE INDEX idx_deliveries_status ON deliveries(status);
CREATE INDEX idx_delivery_logs_delivery ON delivery_logs(delivery_id);
CREATE INDEX idx_delivery_logs_status ON delivery_logs(status);
CREATE INDEX idx_delivery_webhook_logs_processed ON delivery_webhook_logs(processed, created_at);
CREATE INDEX idx_delivery_webhook_logs_fincart ON delivery_webhook_logs(fincart_order_id);

-- Voucher indexes
CREATE INDEX idx_vouchers_code ON vouchers(code);
CREATE INDEX idx_vouchers_active ON vouchers(is_active);

-- =====================================================
-- FUNCTIONS AND TRIGGERS
-- =====================================================

-- Function for automatic timestamp updates
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at timestamps
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_user_addresses_updated_at BEFORE UPDATE ON user_addresses FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_categories_updated_at BEFORE UPDATE ON categories FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_vendors_updated_at BEFORE UPDATE ON vendors FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_products_updated_at BEFORE UPDATE ON products FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_product_inventory_updated_at BEFORE UPDATE ON product_inventory FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_reviews_updated_at BEFORE UPDATE ON reviews FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_hero_images_updated_at BEFORE UPDATE ON hero_images FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_vouchers_updated_at BEFORE UPDATE ON vouchers FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_carts_updated_at BEFORE UPDATE ON carts FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_cart_items_updated_at BEFORE UPDATE ON cart_items FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_orders_updated_at BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Function to clean up old guest carts
CREATE OR REPLACE FUNCTION cleanup_old_guest_carts(days_old INTEGER DEFAULT 30)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM cart_items 
    WHERE cart_id IN (
        SELECT id FROM carts 
        WHERE is_guest = true 
        AND created_at < NOW() - (days_old || ' days')::INTERVAL
    );
    
    DELETE FROM carts 
    WHERE is_guest = true 
    AND created_at < NOW() - (days_old || ' days')::INTERVAL;
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- COMMENTS FOR DOCUMENTATION
-- =====================================================

COMMENT ON TABLE carts IS 'Shopping carts supporting both authenticated users and guest checkout';
COMMENT ON TABLE deliveries IS 'Delivery tracking integrated with Fincart API';
COMMENT ON TABLE delivery_logs IS 'Historical log of all delivery status changes';
COMMENT ON TABLE vendor_payouts IS 'Automated vendor commission payouts via Paymob';
COMMENT ON TABLE payment_transactions IS 'Audit trail for all payment transactions';
COMMENT ON TABLE refund_transactions IS 'Audit trail for refunds and void operations';
COMMENT ON COLUMN deliveries.payment_type IS 'with_cash_collection for COD, without_cash_collection for prepaid';
COMMENT ON COLUMN refund_transactions.refund_type IS 'VOID for unsettled, REFUND for settled transactions';