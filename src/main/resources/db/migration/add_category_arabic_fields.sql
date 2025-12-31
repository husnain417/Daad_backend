-- Migration: Add Arabic name and description fields to categories table
-- Date: 2025-12-31

ALTER TABLE categories 
ADD COLUMN IF NOT EXISTS name_ar VARCHAR(255),
ADD COLUMN IF NOT EXISTS description_ar TEXT;

-- Add comment for documentation
COMMENT ON COLUMN categories.name_ar IS 'Arabic name for the category';
COMMENT ON COLUMN categories.description_ar IS 'Arabic description for the category';

