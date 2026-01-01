#!/bin/bash

echo "Testing Supabase Database Connection..."
echo "=================================="

# Test 1: Session Pooler
echo "1. Testing Session Pooler..."
PGPASSWORD="fPGYM6uBDSSXysKJ" psql -h aws-1-eu-central-1.pooler.supabase.com -p 5432 -U postgres -d postgres -c "SELECT 1 as test;" 2>&1

echo ""
echo "2. Testing Transaction Pooler..."
PGPASSWORD="fPGYM6uBDSSXysKJ" psql -h aws-1-eu-central-1.pooler.supabase.com -p 6543 -U postgres -d postgres -c "SELECT 1 as test;" 2>&1

echo ""
echo "3. Testing Direct Connection..."
PGPASSWORD="fPGYM6uBDSSXysKJ" psql -h db.hlyhuteksgmyovgztfhf.supabase.co -p 5432 -U postgres -d postgres -c "SELECT 1 as test;" 2>&1

echo ""
echo "Test completed. Check which connection works."