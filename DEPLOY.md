# Deployment Documentation - Melkoyai Investment Platform

## Project Overview

**Melkoyai** is a Java-based investment platform built with Quarkus framework, deployed on Render with Supabase PostgreSQL database. This document provides complete deployment instructions and technical configuration details.

## Technology Stack

### Backend Framework
- **Quarkus 3.x** - Supersonic Subatomic Java Framework
- **Java 21** - Eclipse Temurin JDK
- **Maven 3.9.6** - Build and dependency management
- **Hibernate ORM** - Database ORM layer

### Database
- **Supabase PostgreSQL** - Cloud-hosted PostgreSQL database
- **SSL Connection** - Secure database connections
- **Connection Pool** - Optimized database connection management

### Deployment Platform
- **Render** - Cloud application hosting
- **Docker** - Containerized deployment
- **GitHub** - Source code repository and CI/CD trigger

### Security & Configuration
- **SSL/TLS** - Secure connections
- **CORS** - Cross-Origin Resource Sharing
- **Security Headers** - XSS, CSRF protection
- **Environment-based Configuration** - Production-ready settings

## Prerequisites

### Required Accounts
1. **GitHub Account** - For source code repository
2. **Supabase Account** - For PostgreSQL database
3. **Render Account** - For application hosting

### Required Tools (Local Development)
- Java 21 (Eclipse Temurin recommended)
- Maven 3.9+
- Git
- Docker (optional, for local testing)

## Step-by-Step Deployment Guide

### Phase 1: Database Setup (Supabase)

#### 1.1 Create Supabase Project
```bash
# Visit: https://supabase.com/dashboard
# Click "New Project"
# Project Name: melkoyai
# Database Password: 1astigmastism1@a
# Region: Select closest to your users
```

#### 1.2 Import Database Schema
```sql
-- Upload the schema file: supabase-schema.sql
-- This creates 19 tables including:
-- - users (user management)
-- - products (investment products)
-- - transactions (financial transactions)
-- - referrals (referral system)
-- - audit_logs (system auditing)
```

#### 1.3 Get Database Connection Details
```bash
# From Supabase Dashboard > Settings > Database
# Connection String Format:
postgresql://postgres:1astigmastism1@a@db.pkdmonstyusgkjaqzm.supabase.co:5432/postgres
```

### Phase 2: Source Code Repository (GitHub)

#### 2.1 Create GitHub Repository
```bash
# Create new repository: melkoyai
# Initialize with README
# Set to Public or Private as needed
```

#### 2.2 Configure Git Ignore Files
```bash
# .gitignore - Excludes build artifacts, IDE files, sensitive data
# .dockerignore - Optimizes Docker build context
```

#### 2.3 Push Code to Repository
```bash
git init
git add .
git commit -m "Initial commit - Quarkus investment platform"
git branch -M main
git remote add origin https://github.com/edon2000/melkoyai.git
git push -u origin main
```

### Phase 3: Application Configuration

#### 3.1 Database Configuration (application.properties)
```properties
# Database Configuration - Supabase Production
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=postgres
quarkus.datasource.password=1astigmastism1@a
quarkus.datasource.jdbc.url=jdbc:postgresql://db.pkdmonstyusgkjaqzm.supabase.co:5432/postgres?sslmode=require

# Connection Pool Settings
quarkus.datasource.jdbc.initial-size=2
quarkus.datasource.jdbc.min-size=2
quarkus.datasource.jdbc.max-size=5
quarkus.datasource.jdbc.acquisition-timeout=30s
```

#### 3.2 HTTP Configuration
```properties
# Render-Compatible HTTP Settings
quarkus.http.host=0.0.0.0
quarkus.http.port=${PORT:8080}
quarkus.http.insecure-requests=enabled

# Security Headers
quarkus.http.header."X-Frame-Options".value=DENY
quarkus.http.header."X-Content-Type-Options".value=nosniff
quarkus.http.header."X-XSS-Protection".value=1; mode=block
```

#### 3.3 CORS Configuration
```properties
# Cross-Origin Resource Sharing
quarkus.http.cors=true
quarkus.http.cors.origins=*
quarkus.http.cors.methods=GET,POST,PUT,DELETE,OPTIONS
quarkus.http.cors.headers=accept,authorization,content-type,x-requested-with
```

### Phase 4: Docker Configuration

#### 4.1 Multi-Stage Dockerfile
```dockerfile
# Build Stage - Compile Java Application
FROM registry.access.redhat.com/ubi8/openjdk-21:1.19 AS builder

# Install Maven
RUN microdnf update -y && \
    microdnf install -y wget tar gzip && \
    wget https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz && \
    tar xzf apache-maven-3.9.6-bin.tar.gz -C /opt && \
    ln -s /opt/apache-maven-3.9.6 /opt/maven && \
    microdnf clean all

# Set Maven environment
ENV MAVEN_HOME=/opt/maven
ENV PATH=$MAVEN_HOME/bin:$PATH

# Copy source code and build
COPY . /app
WORKDIR /app
RUN mvn clean package -DskipTests

# Runtime Stage - Run Application
FROM registry.access.redhat.com/ubi8/openjdk-21:1.19

# Copy compiled application
COPY --from=builder /app/target/quarkus-app/lib/ /deployments/lib/
COPY --from=builder /app/target/quarkus-app/*.jar /deployments/
COPY --from=builder /app/target/quarkus-app/app/ /deployments/app/
COPY --from=builder /app/target/quarkus-app/quarkus/ /deployments/quarkus/

# Expose port and run
EXPOSE 8080
USER 185
ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
```

#### 4.2 Build Script (build.sh)
```bash
#!/bin/bash
echo "Building Quarkus application..."
./mvnw clean package -DskipTests
echo "Build completed successfully!"
```

### Phase 5: Render Deployment

#### 5.1 Create Render Web Service
```bash
# Visit: https://dashboard.render.com
# Click "New +" > "Web Service"
# Connect GitHub repository: melkoyai
# Configure deployment settings:
```

#### 5.2 Render Configuration
```yaml
# Service Configuration
Name: melkoyai-app
Environment: Docker
Region: Oregon (US West) or closest to users
Branch: main
Root Directory: elonmusk

# Build Configuration
Build Command: (Automatic - uses Dockerfile)
Start Command: (Automatic - from Dockerfile ENTRYPOINT)

# Advanced Settings
Auto-Deploy: Yes (deploys on git push)
```

#### 5.3 Environment Variables (Not Required)
```bash
# Note: This project uses direct configuration in application.properties
# No environment variables needed for database connection
# All configuration is embedded in the application
```

### Phase 6: Java Application Architecture

#### 6.1 Configuration Management
```java
// DatabaseConfig.java - Quarkus Configuration Injection
@ConfigProperty(name = "quarkus.datasource.password")
String databasePassword;

@ConfigProperty(name = "quarkus.datasource.username") 
String databaseUsername;

@ConfigProperty(name = "quarkus.datasource.jdbc.url")
String databaseUrl;
```

#### 6.2 Service Layer Architecture
```java
// Key Services:
- AuthService: User authentication and authorization
- UserService: User management operations
- ProductService: Investment product management
- TransactionService: Financial transaction processing
- BackupService: Database backup management (cloud-aware)
- AuditService: System activity logging
```

#### 6.3 Controller Layer
```java
// REST Controllers:
- AdminController: Administrative operations
- AuthController: Authentication endpoints
- UserController: User management
- ProductController: Product catalog
- TransactionController: Financial operations
```

## Deployment Process Flow

### Automatic Deployment (Recommended)
```bash
1. Developer pushes code to GitHub main branch
2. Render detects changes via webhook
3. Render pulls latest code from repository
4. Docker build process starts:
   - Downloads base image (OpenJDK 21)
   - Installs Maven in build stage
   - Compiles Java application with Maven
   - Creates optimized runtime image
   - Copies compiled artifacts
5. New container deployed automatically
6. Health checks verify deployment success
7. Traffic routed to new deployment
```

### Manual Deployment
```bash
# From Render Dashboard:
1. Go to melkoyai-app service
2. Click "Manual Deploy"
3. Select "Deploy latest commit"
4. Monitor deployment logs
```

## Monitoring and Troubleshooting

### Application Health Checks
```bash
# Health Check Endpoint
GET https://melkoyai-app.onrender.com/health

# Expected Response:
{
  "status": "UP",
  "checks": [
    {
      "name": "Database connection health check",
      "status": "UP"
    }
  ]
}
```

### Log Monitoring
```bash
# Render Dashboard > Service > Logs
# Key log categories:
- Application startup logs
- Database connection logs  
- HTTP request logs
- Error and exception logs
- Security audit logs
```

### Common Issues and Solutions

#### Database Connection Issues
```bash
# Symptom: "Service unavailable" errors
# Cause: Database configuration mismatch
# Solution: Verify application.properties database settings

# Check database connectivity:
1. Verify Supabase project is active
2. Confirm database password in application.properties
3. Ensure SSL mode is set to 'require'
4. Check connection pool settings
```

#### Build Failures
```bash
# Symptom: Docker build fails
# Common causes:
1. Maven dependency resolution issues
2. Java version compatibility
3. Missing source files
4. Network connectivity during build

# Solutions:
- Check pom.xml dependencies
- Verify Java 21 compatibility
- Ensure all source files are committed
- Review Docker build logs
```

#### Performance Issues
```bash
# Database Performance:
- Monitor connection pool usage
- Check query performance in Supabase dashboard
- Review database indexes

# Application Performance:
- Monitor memory usage in Render dashboard
- Check response times
- Review application logs for bottlenecks
```

## Security Considerations

### Database Security
```bash
# SSL/TLS encryption for all database connections
# Connection string includes: ?sslmode=require
# Supabase provides automatic SSL certificate management
```

### Application Security
```bash
# Security headers configured:
- X-Frame-Options: DENY (prevents clickjacking)
- X-Content-Type-Options: nosniff (prevents MIME sniffing)
- X-XSS-Protection: 1; mode=block (XSS protection)
- Strict-Transport-Security: HTTPS enforcement
```

### Access Control
```bash
# Role-based access control implemented
# Admin authentication required for administrative functions
# User session management with secure cookies
# Audit logging for all administrative actions
```

## Performance Optimization

### Database Optimization
```properties
# Connection pool tuning
quarkus.datasource.jdbc.initial-size=2
quarkus.datasource.jdbc.min-size=2  
quarkus.datasource.jdbc.max-size=5
quarkus.datasource.jdbc.acquisition-timeout=30s
```

### Application Optimization
```properties
# JVM optimization for Quarkus
# Native compilation possible for faster startup
# Reduced memory footprint compared to traditional Java apps
```

### Render Optimization
```bash
# Docker multi-stage build reduces image size
# Only runtime dependencies in final image
# Optimized base image (Red Hat UBI)
```

## Backup and Recovery

### Database Backups
```bash
# Supabase provides automatic backups:
- Point-in-time recovery
- Daily automated backups
- Manual backup creation available
- Cross-region backup replication
```

### Application Backups
```bash
# Source code backup:
- GitHub repository serves as primary backup
- Multiple deployment branches possible
- Tag releases for version control
```

## Scaling Considerations

### Horizontal Scaling
```bash
# Render supports:
- Multiple instance deployment
- Load balancing across instances
- Auto-scaling based on traffic
```

### Database Scaling
```bash
# Supabase scaling options:
- Connection pooling (PgBouncer)
- Read replicas for read-heavy workloads
- Vertical scaling (CPU/Memory upgrades)
```

## Cost Optimization

### Render Costs
```bash
# Starter Plan: $7/month
- 512MB RAM
- 0.1 CPU
- Suitable for development/testing

# Standard Plan: $25/month  
- 2GB RAM
- 1 CPU
- Production-ready performance
```

### Supabase Costs
```bash
# Free Tier: $0/month
- 500MB database
- 50MB file storage
- 2GB bandwidth

# Pro Plan: $25/month
- 8GB database
- 100GB file storage
- 250GB bandwidth
```

## Maintenance Procedures

### Regular Updates
```bash
# Monthly maintenance tasks:
1. Update Java dependencies in pom.xml
2. Review Quarkus framework updates
3. Monitor security advisories
4. Update base Docker images
5. Review application logs
6. Performance monitoring review
```

### Security Updates
```bash
# Security maintenance:
1. Monitor CVE databases for Java/Quarkus vulnerabilities
2. Update dependencies with security patches
3. Review access logs for suspicious activity
4. Rotate database passwords periodically
5. Update SSL certificates (handled by Supabase/Render)
```

## Support and Documentation

### Official Documentation
- [Quarkus Documentation](https://quarkus.io/guides/)
- [Supabase Documentation](https://supabase.com/docs)
- [Render Documentation](https://render.com/docs)
- [Java 21 Documentation](https://docs.oracle.com/en/java/javase/21/)

### Community Resources
- [Quarkus Community](https://github.com/quarkusio/quarkus)
- [Supabase Community](https://github.com/supabase/supabase)
- [Stack Overflow](https://stackoverflow.com/questions/tagged/quarkus)

---

## Quick Reference Commands

### Local Development
```bash
# Start development server
./mvnw quarkus:dev

# Build application
./mvnw clean package

# Run tests
./mvnw test

# Build Docker image locally
docker build -t melkoyai .

# Run Docker container locally
docker run -p 8080:8080 melkoyai
```

### Git Operations
```bash
# Deploy to production
git add .
git commit -m "Your commit message"
git push origin main

# Create feature branch
git checkout -b feature/new-feature
git push origin feature/new-feature
```

### Database Operations
```bash
# Connect to Supabase database
psql "postgresql://postgres:1astigmastism1@a@db.pkdmonstyusgkjaqzm.supabase.co:5432/postgres?sslmode=require"

# Run database migrations (if needed)
./mvnw flyway:migrate
```

---

**Deployment Status**: ✅ Production Ready  
**Last Updated**: December 2024  
**Version**: 1.0.0  
**Maintainer**: Development Team