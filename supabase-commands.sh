#!/bin/bash

# Supabase Database Commands
# =========================

DB_HOST="aws-1-eu-central-1.pooler.supabase.com"
DB_PORT="6543"
DB_USER="postgres.hlyhuteksgmyovgztfhf"
DB_NAME="postgres"
DB_PASSWORD="fPGYM6uBDSSXysKJ"

echo "Supabase Database Explorer"
echo "=========================="

# Function to execute SQL commands
execute_sql() {
    PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "$1"
}

# 1. List all tables
echo "1. LISTING ALL TABLES:"
echo "====================="
execute_sql "\dt"

echo ""
echo "2. SHOW TABLE STRUCTURES:"
echo "========================"

# Show structure of key tables
echo "Users table structure:"
execute_sql "\d users"

echo ""
echo "Investment Products table structure:"
execute_sql "\d investment_products"

echo ""
echo "3. COUNT RECORDS IN TABLES:"
echo "=========================="

# Count records in each table
echo "Users count:"
execute_sql "SELECT COUNT(*) as user_count FROM users;"

echo ""
echo "Investment Products count:"
execute_sql "SELECT COUNT(*) as product_count FROM investment_products;"

echo ""
echo "User Investments count:"
execute_sql "SELECT COUNT(*) as investment_count FROM user_investments;"

echo ""
echo "4. SAMPLE DATA:"
echo "=============="

echo "Sample Users (first 5):"
execute_sql "SELECT id, name, phone_number, email, wallet_balance, created_at FROM users LIMIT 5;"

echo ""
echo "Sample Investment Products:"
execute_sql "SELECT id, name, price, daily_return_rate, duration_days, is_active FROM investment_products LIMIT 5;"

echo ""
echo "Recent Transactions (last 5):"
execute_sql "SELECT id, user_id, type, amount, description, processed_at FROM transactions ORDER BY processed_at DESC LIMIT 5;"

echo ""
echo "Database exploration completed!"