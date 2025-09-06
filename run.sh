#!/bin/bash

# Set environment variables
export DB_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres
export DB_USERNAME=postgres.ekgshigtxnbjvzmbwyoe
export DB_PASSWORD=wE0R4Z66iXQh2T4R
export JWT_ACCESS_SECRET="your_super_long_jwt_access_secret_key_here_at_least_256_bits_long_for_security"
export JWT_RESET_SECRET="your_super_long_jwt_reset_secret_key_here_at_least_256_bits_long_for_security"
export SERVER_PORT="5000"

# Email Configuration
export SMTP_HOST="smtp.gmail.com"
export SMTP_PORT="587"
export SMTP_USERNAME="your_email@gmail.com"
export SMTP_PASSWORD="your_app_password_here"

# Print what we're setting
echo "🔧 Setting environment variables..."
echo "📍 DB_URL: $DB_URL"
echo "👤 DB_USERNAME: $DB_USERNAME"
echo "🔐 DB_PASSWORD: ***SET***"
echo "🔑 JWT_ACCESS_SECRET: ***SET***"
echo "🔑 JWT_RESET_SECRET: ***SET***"
echo "🌐 SERVER_PORT: $SERVER_PORT"
echo "📧 SMTP_HOST: $SMTP_HOST"
echo "📧 SMTP_PORT: $SMTP_PORT"
echo "📧 SMTP_USERNAME: $SMTP_USERNAME"
echo "📧 SMTP_PASSWORD: ***SET***"

# Run the application
echo "🚀 Starting Spring Boot application..."
mvn spring-boot:run
