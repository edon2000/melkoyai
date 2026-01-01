# Melkoyai Investment Platform - Deployment Guide

## Quick Deployment Steps

### 1. Database Setup (Supabase PostgreSQL)
- **Host**: `aws-1-eu-central-1.pooler.supabase.com:6543`
- **Database**: `postgres`
- **Username**: `postgres.hlyhuteksgmyovgztfhf`
- **Password**: `fPGYM6uBDSSXysKJ`
- **Connection Type**: Transaction Pooler (IPv4 Compatible)

### 2. Import Database Schema
```bash
# Connect to Supabase database and run:
PGPASSWORD="fPGYM6uBDSSXysKJ" psql -h aws-1-eu-central-1.pooler.supabase.com -p 6543 -U postgres.hlyhuteksgmyovgztfhf -d postgres -f supabase-schema.sql
```

### 3. Deploy to Render
1. **Push code to GitHub** (automatic deployment)
2. **Application URL**: https://melkoyai-app.onrender.com
3. **Admin Dashboard**: https://melkoyai-app.onrender.com/million/dashboard

### 4. Technology Stack
- **Framework**: Quarkus 3.8.3 (Java 21)
- **Database**: Supabase PostgreSQL
- **Hosting**: Render
- **Container**: Docker

### 5. Key Features
- Investment platform with multiple product tiers
- User registration and referral system
- Admin dashboard for management
- Gift code generation and redemption
- Secure authentication and audit logging

---
**Status**: Production Ready ✅