-- Add website_sync_url column to vendors table
ALTER TABLE vendors ADD COLUMN website_sync_url TEXT;
ALTER TABLE vendors ADD COLUMN request_token TEXT;
