-- PostgreSQL Schema for E-commerce Platform
-- Run these commands in order

-- Enable necessary extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Create ENUM types
CREATE TYPE user_role AS ENUM ('customer', 'admin', 'vendor');
CREATE TYPE auth_provider AS ENUM ('local', 'google');
CREATE TYPE address_type AS ENUM ('home', 'work', 'other');
CREATE TYPE business_type AS ENUM ('individual', 'business', 'company');
CREATE TYPE vendor_status AS ENUM ('pending', 'approved', 'rejected', 'suspended');
CREATE TYPE product_gender AS ENUM ('Men', 'Women', 'Unisex', 'Male', 'Female');
CREATE TYPE product_status AS ENUM ('none', 'draft', 'awaiting_approval', 'approved', 'rejected');
CREATE TYPE approval_action AS ENUM ('approve', 'reject');
CREATE TYPE page_type AS ENUM ('home', 'mens', 'womens');
CREATE TYPE view_type AS ENUM ('web', 'mobile');
CREATE TYPE review_status AS ENUM ('pending', 'approved', 'rejected');
CREATE TYPE voucher_type AS ENUM ('percentage', 'fixed');
CREATE TYPE voucher_applicable AS ENUM ('all', 'category', 'brand', 'vendor', 'first-order', 'student');
CREATE TYPE payment_method AS ENUM ('cash-on-delivery', 'bank-transfer', 'card');
CREATE TYPE payment_status AS ENUM ('pending', 'paid', 'failed');
CREATE TYPE order_status AS ENUM ('pending', 'confirmed', 'processing', 'shipped', 'delivered', 'cancelled');

-- 1. Users table
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

-- 2. User addresses table
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

-- 3. Categories table
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

-- 4. Vendors table
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
    commission DECIMAL(5,2) DEFAULT 10.00,
    rating_average DECIMAL(3,2) DEFAULT 0.00,
    rating_count INTEGER DEFAULT 0,
    profile_completed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- 7. Products table
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL CHECK (price >= 0),
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    vendor_id UUID REFERENCES vendors(id) ON DELETE CASCADE,
    gender product_gender DEFAULT 'none',
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

-- 6. Product colors table
CREATE TABLE product_colors (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(7), -- hex code
    is_available BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- 7. Product sizes table
CREATE TABLE product_sizes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    is_available BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- 8. Product inventory table (improved structure)
CREATE TABLE product_inventory (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    color VARCHAR(100) NOT NULL,
    color_code VARCHAR(7), -- hex code for color
    size VARCHAR(50) NOT NULL,
    stock INTEGER DEFAULT 0 CHECK (stock >= 0),
    is_available BOOLEAN DEFAULT TRUE,
    min_stock_threshold INTEGER DEFAULT 5, -- for low stock alerts
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(product_id, color, size)
);

-- 9. Product images table
CREATE TABLE product_images (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    color VARCHAR(100), -- NULL for default images
    url TEXT NOT NULL,
    alt_text TEXT,
    file_id VARCHAR(255), -- for tracking and deletion
    is_primary BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- 10. Product what's in box table
CREATE TABLE product_whats_in_box (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    image_url TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- 11. Product related products table (many-to-many)
CREATE TABLE product_related (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    related_product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(product_id, related_product_id)
);

-- 12. Reviews table
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

-- 13. Review images table
CREATE TABLE review_images (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    review_id UUID REFERENCES reviews(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    public_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW()
);

-- 14. Review helpful votes table
CREATE TABLE review_helpful_votes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    review_id UUID REFERENCES reviews(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(review_id, user_id)
);

-- 15. Hero images table
CREATE TABLE hero_images (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    page_type page_type NOT NULL,
    view_type view_type NOT NULL,
    image_url TEXT NOT NULL,
    cloudinary_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(page_type, view_type)
);

-- 16. Subscribers table
CREATE TABLE subscribers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- 17. Vouchers table
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

-- 18. Voucher applicable items table
CREATE TABLE voucher_applicable_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    voucher_id UUID REFERENCES vouchers(id) ON DELETE CASCADE,
    applicable_id UUID NOT NULL, -- Can reference categories, vendors, etc.
    created_at TIMESTAMP DEFAULT NOW()
);

-- 19. Carts table
CREATE TABLE carts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID UNIQUE REFERENCES users(id) ON DELETE CASCADE,
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
    estimated_delivery_min_days INTEGER,
    estimated_delivery_max_days INTEGER,
    last_updated TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- 20. Cart items table
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

-- 21. Cart applied vouchers table
CREATE TABLE cart_applied_vouchers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cart_id UUID REFERENCES carts(id) ON DELETE CASCADE,
    voucher_id UUID REFERENCES vouchers(id) ON DELETE CASCADE,
    discount_amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- 22. Orders table
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id), -- NULL for guest orders
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
    order_status order_status DEFAULT 'pending',
    is_first_order BOOLEAN DEFAULT FALSE,
    tracking_number VARCHAR(255),
    estimated_delivery TIMESTAMP,
    delivered_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancellation_reason TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- 23. Order items table
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

-- Create indexes for better performance
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_user_addresses_user_id ON user_addresses(user_id);
CREATE INDEX idx_categories_slug ON categories(slug);
CREATE INDEX idx_categories_parent ON categories(parent_category_id);
CREATE INDEX idx_vendors_user_id ON vendors(user_id);
CREATE INDEX idx_vendors_status ON vendors(status);
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
CREATE INDEX idx_reviews_product ON reviews(product_id);
CREATE INDEX idx_reviews_customer ON reviews(customer_id);
CREATE INDEX idx_reviews_status ON reviews(status);
CREATE INDEX idx_carts_user ON carts(user_id);
CREATE INDEX idx_cart_items_cart ON cart_items(cart_id);
CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(order_status);
CREATE INDEX idx_orders_payment_status ON orders(payment_status);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_vouchers_code ON vouchers(code);
CREATE INDEX idx_vouchers_active ON vouchers(is_active);

-- Create functions for automatic timestamp updates
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




CHANGES::

-- Updated Carts table to support both logged-in users and guests
-- Drop the existing carts table and recreate
DROP TABLE IF EXISTS cart_applied_vouchers CASCADE;
DROP TABLE IF EXISTS cart_items CASCADE;
DROP TABLE IF EXISTS carts CASCADE;

-- 19. Updated Carts table (supports both users and guests)
CREATE TABLE carts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE, -- NULL for guest carts
    cart_id UUID, -- Used for guest carts
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
    shipping_email VARCHAR(255), -- Added email field
    estimated_delivery_min_days INTEGER,
    estimated_delivery_max_days INTEGER,
    last_updated TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    -- Constraints to ensure either user_id or cart_id is set, but not both
    CONSTRAINT check_user_or_guest CHECK (
        (user_id IS NOT NULL AND cart_id IS NULL AND is_guest = FALSE) OR
        (user_id IS NULL AND cart_id IS NOT NULL AND is_guest = TRUE)
    ),
    
    -- Unique constraint for user carts
    CONSTRAINT unique_user_cart UNIQUE (user_id),
    
    -- Unique constraint for guest carts
    CONSTRAINT unique_guest_cart UNIQUE (cart_id)
);

-- 20. Updated Cart items table
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

-- 21. Updated Cart applied vouchers table
CREATE TABLE cart_applied_vouchers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cart_id UUID REFERENCES carts(id) ON DELETE CASCADE,
    voucher_id UUID REFERENCES vouchers(id) ON DELETE CASCADE,
    discount_amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Create updated indexes
CREATE INDEX idx_carts_user ON carts(user_id) WHERE is_guest = false;
CREATE INDEX idx_carts_guest ON carts(cart_id) WHERE is_guest = true;
CREATE INDEX idx_carts_guest_flag ON carts(is_guest);
CREATE INDEX idx_carts_created_at ON carts(created_at); -- For cleanup of old guest carts
CREATE INDEX idx_cart_items_cart ON cart_items(cart_id);

-- Create trigger for updated_at timestamp on carts
CREATE TRIGGER update_carts_updated_at BEFORE UPDATE ON carts FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_cart_items_updated_at BEFORE UPDATE ON cart_items FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Function to clean up old guest carts (can be called by a scheduled job)
CREATE OR REPLACE FUNCTION cleanup_old_guest_carts(days_old INTEGER DEFAULT 30)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    -- Delete cart items first
    DELETE FROM cart_items 
    WHERE cart_id IN (
        SELECT id FROM carts 
        WHERE is_guest = true 
        AND created_at < NOW() - (days_old || ' days')::INTERVAL
    );
    
    -- Delete carts and get count
    DELETE FROM carts 
    WHERE is_guest = true 
    AND created_at < NOW() - (days_old || ' days')::INTERVAL;
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;


//2
-- Update the hero_images table to better reflect B2 usage
ALTER TABLE hero_images RENAME COLUMN cloudinary_id TO storage_key;
ALTER TABLE hero_images ADD COLUMN IF NOT EXISTS storage_provider VARCHAR(50) DEFAULT 'backblaze_b2';