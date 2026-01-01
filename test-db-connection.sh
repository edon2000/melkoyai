#!/bin/bash

echo "Testing Supabase Database Connection..."
echo "=================================="

# Test 1: Session Pooler (port 5432)
echo "1. Testing Session Pooler (postgres username)..."
PGPASSWORD="fPGYM6uBDSSXysKJ" psql -h aws-1-eu-central-1.pooler.supabase.com -p 5432 -U postgres -d postgres -c "SELECT 1 as test;" 2>&1

echo ""
echo "2. Testing Transaction Pooler (postgres.project username)..."
PGPASSWORD="fPGYM6uBDSSXysKJ" psql -h aws-1-eu-central-1.pooler.supabase.com -p 6543 -U postgres.hlyhuteksgmyovgztfhf -d postgres -c "SELECT 1 as test;" 2>&1

echo ""
echo "3. Testing Direct Connection..."
PGPASSWORD="fPGYM6uBDSSXysKJ" psql -h db.hlyhuteksgmyovgztfhf.supabase.co -p 5432 -U postgres -d postgres -c "SELECT 1 as test;" 2>&1

echo ""
echo "Test completed. Check which connection works."