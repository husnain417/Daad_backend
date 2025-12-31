# Running the Product Arabic Fields Migration

## Problem
The database is missing the `name_ar` and `description_ar` columns in the `products` table, which causes errors when updating products with Arabic translations.

## Solution
Run the migration SQL script to add these columns.

## Option 1: Supabase SQL Editor (Easiest)

1. Go to your Supabase project dashboard
2. Navigate to **SQL Editor**
3. Copy and paste the following SQL:

```sql
ALTER TABLE products 
ADD COLUMN IF NOT EXISTS name_ar VARCHAR(255),
ADD COLUMN IF NOT EXISTS description_ar TEXT;

COMMENT ON COLUMN products.name_ar IS 'Arabic name for the product';
COMMENT ON COLUMN products.description_ar IS 'Arabic description for the product';
```

4. Click **Run** to execute the migration

## Option 2: Command Line (psql)

If you have PostgreSQL client tools installed:

```bash
cd Daad_backend
psql -h aws-0-ap-southeast-1.pooler.supabase.com -p 6543 -U postgres.ekgshigtxnbjvzmbwyoe -d postgres -f src/main/resources/db/migration/add_product_arabic_fields.sql
```

You'll be prompted for the database password.

## Option 3: Direct SQL Connection

You can also connect to your database using any PostgreSQL client and run the SQL from:
`src/main/resources/db/migration/add_product_arabic_fields.sql`

## Verification

After running the migration, verify the columns exist:

```sql
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'products' 
AND column_name IN ('name_ar', 'description_ar');
```

You should see both columns listed.

## After Migration

Once the migration is complete:
1. Restart your Spring Boot backend server
2. Try updating a product again - it should work now!

