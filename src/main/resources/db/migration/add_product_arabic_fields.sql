-- Migration: Add Arabic name and description fields to products table
-- Date: 2025-12-31

ALTER TABLE products 
ADD COLUMN IF NOT EXISTS name_ar VARCHAR(255),
ADD COLUMN IF NOT EXISTS description_ar TEXT;

-- Add comment for documentation
COMMENT ON COLUMN products.name_ar IS 'Arabic name for the product';
COMMENT ON COLUMN products.description_ar IS 'Arabic description for the product';

