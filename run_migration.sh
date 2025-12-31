#!/bin/bash

# Script to run the product Arabic fields migration
# This adds name_ar and description_ar columns to the products table

echo "🔄 Running product Arabic fields migration..."

# Read database connection from .env file
if [ -f .env ]; then
    source .env
    DB_URL=${DB_URL#jdbc:postgresql://}
    DB_HOST_PORT=${DB_URL%%/*}
    DB_NAME=${DB_URL#*/}
    DB_HOST=${DB_HOST_PORT%%:*}
    DB_PORT=${DB_HOST_PORT#*:}
    
    echo "📊 Connecting to database: $DB_NAME on $DB_HOST:$DB_PORT"
    
    # Run migration using psql
    PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME -f src/main/resources/db/migration/add_product_arabic_fields.sql
    
    if [ $? -eq 0 ]; then
        echo "✅ Migration completed successfully!"
    else
        echo "❌ Migration failed. Please check the error above."
        exit 1
    fi
else
    echo "❌ .env file not found. Please create it first."
    exit 1
fi

