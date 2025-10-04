-- Supabase RLS Policies for Backend API Access
-- Run these after creating the schema

-- Enable RLS on all tables
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_addresses ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE vendors ENABLE ROW LEVEL SECURITY;
ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_colors ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_sizes ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_inventory ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_images ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_whats_in_box ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_related ENABLE ROW LEVEL SECURITY;
ALTER TABLE reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE review_images ENABLE ROW LEVEL SECURITY;
ALTER TABLE review_helpful_votes ENABLE ROW LEVEL SECURITY;
ALTER TABLE hero_images ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscribers ENABLE ROW LEVEL SECURITY;
ALTER TABLE vouchers ENABLE ROW LEVEL SECURITY;
ALTER TABLE voucher_applicable_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE carts ENABLE ROW LEVEL SECURITY;
ALTER TABLE cart_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE cart_applied_vouchers ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_items ENABLE ROW LEVEL SECURITY;

-- Create service role policy (allows backend full access)
-- This assumes your backend uses the service role key

-- Users table policies
CREATE POLICY "Service role can manage users" ON users FOR ALL TO service_role USING (true);
CREATE POLICY "Users can view own data" ON users FOR SELECT USING (auth.uid()::text = id::text);
CREATE POLICY "Users can update own data" ON users FOR UPDATE USING (auth.uid()::text = id::text);

-- User addresses policies
CREATE POLICY "Service role can manage addresses" ON user_addresses FOR ALL TO service_role USING (true);
CREATE POLICY "Users can manage own addresses" ON user_addresses FOR ALL USING (auth.uid()::text = user_id::text);

-- Categories policies (public read, admin write)
CREATE POLICY "Service role can manage categories" ON categories FOR ALL TO service_role USING (true);
CREATE POLICY "Anyone can view active categories" ON categories FOR SELECT USING (is_active = true);

-- Vendors policies
CREATE POLICY "Service role can manage vendors" ON vendors FOR ALL TO service_role USING (true);
CREATE POLICY "Vendors can view own data" ON vendors FOR SELECT USING (auth.uid()::text = user_id::text);
CREATE POLICY "Vendors can update own data" ON vendors FOR UPDATE USING (auth.uid()::text = user_id::text);

-- Products policies
CREATE POLICY "Service role can manage products" ON products FOR ALL TO service_role USING (true);
CREATE POLICY "Anyone can view active products" ON products FOR SELECT USING (is_active = true AND status = 'approved');
CREATE POLICY "Vendors can manage own products" ON products FOR ALL USING (
  EXISTS (
    SELECT 1 FROM vendors 
    WHERE vendors.id = products.vendor_id 
    AND vendors.user_id::text = auth.uid()::text
  )
);

-- Product colors policies
CREATE POLICY "Service role can manage product colors" ON product_colors FOR ALL TO service_role USING (true);
CREATE POLICY "Anyone can view product colors" ON product_colors FOR SELECT USING (
  EXISTS (
    SELECT 1 FROM products 
    WHERE products.id = product_colors.product_id 
    AND products.is_active = true 
    AND products.status = 'approved'
  )
);

-- Product sizes policies
CREATE POLICY "Service role can manage product sizes" ON product_sizes FOR ALL TO service_role USING (true);
CREATE POLICY "Anyone can view product sizes" ON product_sizes FOR SELECT USING (
  EXISTS (
    SELECT 1 FROM products 
    WHERE products.id = product_sizes.product_id 
    AND products.is_active = true 
    AND products.status = 'approved'
  )
);

-- Product inventory policies
CREATE POLICY "Service role can manage inventory" ON product_inventory FOR ALL TO service_role USING (true);
CREATE POLICY "Anyone can view inventory" ON product_inventory FOR SELECT USING (
  EXISTS (
    SELECT 1 FROM products 
    WHERE products.id = product_inventory.product_id 
    AND products.is_active = true 
    AND products.status = 'approved'
  )
);

-- Product images policies
CREATE POLICY "Service role can manage product images" ON product_images FOR ALL TO service_role USING (true);
CREATE POLICY "Anyone can view product images" ON product_images FOR SELECT USING (
  EXISTS (
    SELECT 1 FROM products 
    WHERE products.id = product_images.product_id 
    AND products.is_active = true 
    AND products.status = 'approved'
  )
);

-- Product what's in box policies
CREATE POLICY "Service role can manage whats in box" ON product_whats_in_box FOR ALL TO service_role USING (true);
CREATE POLICY "Anyone can view whats in box" ON product_whats_in_box FOR SELECT USING (
  EXISTS (
    SELECT 1 FROM products 
    WHERE products.id = product_whats_in_box.product_id 
    AND products.is_active = true 
    AND products.status = 'approved'
  )
);

-- Product related policies
CREATE POLICY "Service role can manage related products" ON product_related FOR ALL TO service_role USING (true);
CREATE POLICY "Anyone can view related products" ON product_related FOR SELECT USING (true);

-- Reviews policies
CREATE POLICY "Service role can manage reviews" ON reviews FOR ALL TO service_role USING (true);
CREATE POLICY "Anyone can view approved reviews" ON reviews FOR SELECT USING (status = 'approved');
CREATE POLICY "Users can create reviews" ON reviews FOR INSERT WITH CHECK (auth.uid()::text = customer_id::text);
CREATE POLICY "Users can update own reviews" ON reviews FOR UPDATE USING (auth.uid()::text = customer_id::text);

-- Review images policies
CREATE POLICY "Service role can manage review images" ON review_images FOR ALL TO service_role USING (true);
CREATE POLICY "Anyone can view review images" ON review_images FOR SELECT USING (
  EXISTS (
    SELECT 1 FROM reviews 
    WHERE reviews.id = review_images.review_id 
    AND reviews.status = 'approved'
  )
);

-- Review helpful votes policies
CREATE POLICY "Service role can manage helpful votes" ON review_helpful_votes FOR ALL TO service_role USING (true);
CREATE POLICY "Users can manage own votes" ON review_helpful_votes FOR ALL USING (auth.uid()::text = user_id::text);

-- Hero images policies
CREATE POLICY "Service role can manage hero images" ON hero_images FOR ALL TO service_role USING (true);
CREATE POLICY "Anyone can view hero images" ON hero_images FOR SELECT USING (true);

-- Subscribers policies
CREATE POLICY "Service role can manage subscribers" ON subscribers FOR ALL TO service_role USING (true);
CREATE POLICY "Anyone can subscribe" ON subscribers FOR INSERT WITH CHECK (true);

-- Vouchers policies
CREATE POLICY "Service role can manage vouchers" ON vouchers FOR ALL TO service_role USING (true);
CREATE POLICY "Anyone can view active vouchers" ON vouchers FOR SELECT USING (is_active = true);

-- Voucher applicable items policies
CREATE POLICY "Service role can manage voucher items" ON voucher_applicable_items FOR ALL TO service_role USING (true);

-- Carts policies
CREATE POLICY "Service role can manage carts" ON carts FOR ALL TO service_role USING (true);
CREATE POLICY "Users can manage own cart" ON carts FOR ALL USING (auth.uid()::text = user_id::text);

-- Cart items policies
CREATE POLICY "Service role can manage cart items" ON cart_items FOR ALL TO service_role USING (true);
CREATE POLICY "Users can manage own cart items" ON cart_items FOR ALL USING (
  EXISTS (
    SELECT 1 FROM carts 
    WHERE carts.id = cart_items.cart_id 
    AND carts.user_id::text = auth.uid()::text
  )
);

-- Cart applied vouchers policies
CREATE POLICY "Service role can manage cart vouchers" ON cart_applied_vouchers FOR ALL TO service_role USING (true);
CREATE POLICY "Users can manage own cart vouchers" ON cart_applied_vouchers FOR ALL USING (
  EXISTS (
    SELECT 1 FROM carts 
    WHERE carts.id = cart_applied_vouchers.cart_id 
    AND carts.user_id::text = auth.uid()::text
  )
);

-- Orders policies
CREATE POLICY "Service role can manage orders" ON orders FOR ALL TO service_role USING (true);
CREATE POLICY "Users can view own orders" ON orders FOR SELECT USING (auth.uid()::text = user_id::text);

-- Order items policies
CREATE POLICY "Service role can manage order items" ON order_items FOR ALL TO service_role USING (true);
CREATE POLICY "Users can view own order items" ON order_items FOR SELECT USING (
  EXISTS (
    SELECT 1 FROM orders 
    WHERE orders.id = order_items.order_id 
    AND orders.user_id::text = auth.uid()::text
  )
);

-- Additional policies for anonymous users (guest checkout, public browsing)
CREATE POLICY "Anonymous users can view public data" ON products FOR SELECT TO anon USING (is_active = true AND status = 'approved');
CREATE POLICY "Anonymous users can view categories" ON categories FOR SELECT TO anon USING (is_active = true);
CREATE POLICY "Anonymous users can view hero images" ON hero_images FOR SELECT TO anon USING (true);
CREATE POLICY "Anonymous users can subscribe" ON subscribers FOR INSERT TO anon WITH CHECK (true);

-- Grant necessary permissions to service role
GRANT ALL ON ALL TABLES IN SCHEMA public TO service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO service_role;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO service_role;

-- Grant read permissions to authenticated users
GRANT SELECT ON ALL TABLES IN SCHEMA public TO authenticated;

-- Grant limited permissions to anonymous users
GRANT SELECT ON categories, products, product_colors, product_sizes, product_inventory, product_images, product_whats_in_box, product_related, reviews, review_images, hero_images TO anon;
GRANT INSERT ON subscribers TO anon;